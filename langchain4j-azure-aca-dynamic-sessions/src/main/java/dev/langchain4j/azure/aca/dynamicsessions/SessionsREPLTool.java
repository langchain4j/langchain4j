package dev.langchain4j.azure.aca.dynamicsessions;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.code.CodeExecutionEngine;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpMethod;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.spi.ServiceHelper;
import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * A tool for executing code in Azure ACA dynamic sessions.
 * See the examples here for more information:
 * https://github.com/langchain4j/langchain4j-examples/tree/main/azure-aca-dynamic-sessions-examples
 *
 * Overview:
 * SessionsREPLTool provides a mechanism to execute code snippets within an Azure
 * Container Apps (ACA) dynamic session. It facilitates interaction with a remote code execution environment,
 * allowing for tasks such as performing calculations, running scripts, and managing files.
 * This class implements {@link CodeExecutionEngine}, making it compatible with the standard
 * code execution interfaces in langchain4j.
 *
 * Key Components:
 *   USER_AGENT:  A static string defining the User-Agent header for HTTP requests.
 *   API_VERSION:  A static string specifying the API version for interacting with the ACA dynamic sessions endpoint.
 *   SANITIZE_PATTERN_START and SANITIZE_PATTERN_END:  Static patterns used to sanitize input code by removing leading/trailing whitespace or keywords.
 *   sanitizeInput:  A boolean flag indicating whether to sanitize the input code before execution.
 *   poolManagementEndpoint:  The URL of the pool management endpoint for the ACA dynamic sessions.
 *   sessionId:  The unique identifier for the ACA dynamic session.
 *   nativeHttpClient:  A standard Java HttpClient instance for multipart form data operations.
 *   langchainHttpClient:  A langchain4j HttpClient abstraction for standard HTTP operations.
 *   credential:  A DefaultAzureCredential instance for authenticating with Azure services.
 *   accessTokenRef:  An AtomicReference to store and manage the access token.
 *   FileUploader, FileDownloader, FileLister:  Interfaces defining file management operations.
 *   FileUploaderImpl, FileDownloaderImpl, FileListerImpl:  Implementations of the file management interfaces.
 *   RemoteFileMetadata:  A class representing metadata for remote files.
 *
 * Functionality:
 * The SessionsREPLTool class provides the following core functionalities:
 *   Code Execution:  Executes code snippets within the ACA dynamic session and returns the results.
 *      - Implements {@link CodeExecutionEngine#execute(String)} for standardized execution API
 *      - Provides a {@link Tool} annotated {@link #use(String)} method for integration with AI services
 *   File Management:  Allows uploading, downloading, and listing files within the session's environment.
 *   Authentication:  Handles authentication with Azure services using the DefaultAzureCredential.
 *   Input Sanitization:  Provides an option to sanitize input code for security and compatibility.
 *
 * Usage:
 * To use the SessionsREPLTool, you need to:
 *  Create an instance of the class, providing the pool management endpoint.
 *  Call the use() or execute() method to execute code, passing the code snippet as a string.
 *  Use the file management interfaces and implementations to interact with files in the session.
 *  Ensure proper error handling and resource management.
 *  For testing, use the protected constructor that accepts pre-configured dependencies.
 */
public class SessionsREPLTool implements CodeExecutionEngine {

    private static final String USER_AGENT = "langchain4j-azure-dynamic-sessions/1.0.0-beta1 (Language=Java)";
    private static final String API_VERSION = "2024-02-02-preview";

    private static final Pattern SANITIZE_PATTERN_START = Pattern.compile("^(\\s|`)*(?i:python)?\\s*");
    private static final Pattern SANITIZE_PATTERN_END = Pattern.compile("(\\s|`)*$");

    private final boolean sanitizeInput;
    private final String poolManagementEndpoint;
    private final String sessionId;
    private final java.net.http.HttpClient nativeHttpClient;
    private final HttpClient langchainHttpClient;
    private final DefaultAzureCredential credential;
    private final AtomicReference<AccessToken> accessTokenRef = new AtomicReference<>();

    /**
     * Constructs a new SessionsREPLTool with the specified endpoint.
     *
     * @param poolManagementEndpoint the pool management endpoint URL
     */
    public SessionsREPLTool(String poolManagementEndpoint) {
        this(poolManagementEndpoint, UUID.randomUUID().toString(), true);
    }

    /**
     * Interface for uploading files to ACA.
     */
    public interface FileUploader {
        /**
         * Uploads a local file to Azure Container Apps.
         *
         * @param localFilePath the path to the local file to upload
         * @return metadata about the uploaded file
         */
        RemoteFileMetadata uploadFileToAca(Path localFilePath);
    }

    /**
     * Interface for downloading files from ACA.
     */
    public interface FileDownloader {
        /**
         * Downloads a file from Azure Container Apps.
         *
         * @param remoteFilePath the path of the file to download
         * @return the content of the downloaded file
         */
        String downloadFile(String remoteFilePath);
    }

    /**
     * Interface for listing files in ACA.
     */
    public interface FileLister {
        /**
         * Lists all files in the Azure Container Apps session.
         *
         * @return a string representation of the file listing
         */
        String listFiles();
    }

    /**
     * Implementation of CodeExecutionEngine.execute method
     * @param code The code to execute.
     * @return The result of the execution as a String in JSON format.
     */
    @Override
    public String execute(String code) {
        // Use the existing 'use' method which returns a JSON-formatted string
        return use(code);
    }

    /**
     * Constructs a new SessionsREPLTool with specified parameters.
     *
     * @param poolManagementEndpoint the pool management endpoint URL
     * @param sessionId the session ID
     * @param sanitizeInput whether to sanitize the input code
     */
    public SessionsREPLTool(String poolManagementEndpoint, String sessionId, boolean sanitizeInput) {
        this.poolManagementEndpoint = poolManagementEndpoint;
        this.sessionId = sessionId;
        this.sanitizeInput = sanitizeInput;
        this.nativeHttpClient = java.net.http.HttpClient.newBuilder().build();

        // The loadFactories() method returns a Collection, not a List,
        // so we need to use the iterator instead of get(0)
        Collection<HttpClientBuilder> builders = ServiceHelper.loadFactories(HttpClientBuilder.class);
        if (builders.isEmpty()) {
            throw new IllegalStateException(
                    "No HttpClientBuilder implementation found. Make sure you have a proper implementation on the classpath.");
        }
        this.langchainHttpClient = builders.iterator().next().build(); // Use the langchain4j HTTP client abstraction

        this.credential = new DefaultAzureCredentialBuilder().build();
    }

    /**
     * Protected constructor for testing purposes that allows injecting dependencies.
     *
     * @param poolManagementEndpoint the pool management endpoint URL
     * @param sessionId the session ID
     * @param sanitizeInput whether to sanitize the input code
     * @param httpClient a pre-configured HTTP client
     * @param credential a pre-configured credential
     */
    protected SessionsREPLTool(
            String poolManagementEndpoint,
            String sessionId,
            boolean sanitizeInput,
            HttpClient httpClient,
            DefaultAzureCredential credential) {
        this.poolManagementEndpoint = poolManagementEndpoint;
        this.sessionId = sessionId;
        this.sanitizeInput = sanitizeInput;
        this.nativeHttpClient = java.net.http.HttpClient.newBuilder().build();
        this.langchainHttpClient = httpClient;
        this.credential = credential;
    }

    // Method is now a no-op as langchain4j HTTP client handles resource cleanup internally
    public void shutdown() {
        // No manual cleanup needed for the HTTP client
    }

    /**
     * Executes the input code and returns the result.
     *
     * @param input the code or query to execute
     * @return the execution result as a JSON string
     */
    @Tool(name = "sessions_REPL")
    public String use(String input) {
        Map<String, Object> response = executeCode(input);

        Object result = response.get("result");
        if (result instanceof Map<?, ?>) {
            Map<?, ?> resultMap = (Map<?, ?>) result;
            if ("image".equals(resultMap.get("type")) && resultMap.containsKey("base64_data")) {
                resultMap.remove("base64_data");
            }
        }

        Map<String, Object> contentMap = new HashMap<>();
        contentMap.put("result", result);
        contentMap.put("stdout", response.get("stdout"));
        contentMap.put("stderr", response.get("stderr"));

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(contentMap);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize response.", e);
        }
    }

    /**
     * Returns the value of the tool.
     *
     * @return an array containing the tool name
     */
    public String[] value() {
        return new String[] {"REPL Tool"};
    }

    private String getAccessToken() {
        AccessToken token = accessTokenRef.get();
        if (token == null || token.isExpired()) {
            try {
                TokenRequestContext context =
                        new TokenRequestContext().addScopes("https://dynamicsessions.io/.default");
                token = credential.getToken(context).block();
                if (token != null) {
                    accessTokenRef.set(token);
                } else {
                    throw new RuntimeException("Failed to acquire access token.");
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to acquire access token.", e);
            }
        }
        return token.getToken();
    }

    private String buildUrl(String path) {
        String endpoint = poolManagementEndpoint;
        if (!endpoint.endsWith("/")) {
            endpoint += "/";
        }
        String encodedSessionId = URLEncoder.encode(sessionId, StandardCharsets.UTF_8);
        String query = "identifier=" + encodedSessionId + "&api-version=" + API_VERSION;
        return endpoint + path + "?" + query;
    }

    private String sanitizeInput(String input) {
        input = SANITIZE_PATTERN_START.matcher(input).replaceAll("");
        input = SANITIZE_PATTERN_END.matcher(input).replaceAll("");
        return input;
    }

    /**
     * Executes a query to the code interpreter.
     *
     * @param sessionCode the code or query to execute
     * @return a map containing the execution results
     */
    public Map<String, Object> executeCode(String sessionCode) {
        if (sanitizeInput) {
            sessionCode = sanitizeInput(sessionCode);
        }

        String accessToken = getAccessToken();
        String apiUrl = buildUrl("code/execute");

        Map<String, Object> body = new HashMap<>();
        Map<String, Object> properties = new HashMap<>();
        properties.put("codeInputType", "inline");
        properties.put("executionType", "synchronous");
        properties.put("code", sessionCode);
        body.put("properties", properties);

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            String requestBody = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.builder()
                    .method(HttpMethod.POST)
                    .url(apiUrl)
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("User-Agent", USER_AGENT)
                    .body(requestBody)
                    .build();

            SuccessfulHttpResponse response = langchainHttpClient.execute(request);

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                Map<String, Object> responseJson = objectMapper.readValue(response.body(), Map.class);
                return (Map<String, Object>) responseJson.get("properties");
            } else {
                throw new RuntimeException(
                        "Request failed with status code " + response.statusCode() + ": " + response.body());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute code.", e);
        }
    }

    /**
     * Implementation of the FileUploader interface that uploads files to Azure Container Apps.
     */
    public class FileUploaderImpl implements FileUploader {

        @Override
        public RemoteFileMetadata uploadFileToAca(Path localFilePath) {
            String remoteFilePath = localFilePath.getFileName().toString();
            System.out.println("Found file at local path: " + remoteFilePath);
            String accessToken = getAccessToken();
            String apiUrl = buildUrl("files/upload");
            System.out.println("Uploading: API URL:" + apiUrl);

            File file = localFilePath.toFile(); // Convert Path to File

            try {
                // Note: For multipart/form-data uploads, we need to use the native Java HttpClient
                // as the langchain4j HttpClient abstraction doesn't directly support multipart yet
                java.net.http.HttpRequest.BodyPublisher publisher =
                        java.net.http.HttpRequest.BodyPublishers.ofFile(localFilePath);

                String boundary = "----" + System.currentTimeMillis();
                String contentDisposition =
                        "Content-Disposition: form-data; name=\"file\"; filename=\"" + file.getName() + "\"";

                byte[] fileBytes = Files.readAllBytes(localFilePath);
                String separator = "--" + boundary + "\r\n";
                String contentType = "Content-Type: application/json\r\n\r\n";
                String end = "\r\n--" + boundary + "--\r\n";

                byte[] formDataBytes =
                        (separator + contentDisposition + "\r\n" + contentType).getBytes(StandardCharsets.UTF_8);
                byte[] endBytes = end.getBytes(StandardCharsets.UTF_8);

                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                outputStream.write(formDataBytes);
                outputStream.write(fileBytes);
                outputStream.write(endBytes);
                byte[] requestBody = outputStream.toByteArray();

                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl))
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(requestBody))
                        .build();

                java.net.http.HttpResponse<String> response =
                        nativeHttpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IOException("Unexpected code " + response);
                }

                ObjectMapper objectMapper = new ObjectMapper();
                Map<String, Object> responseJson = objectMapper.readValue(response.body(), Map.class);
                List<Map<String, Object>> valueList = (List<Map<String, Object>>) responseJson.get("value");
                Map<String, Object> fileMetadataMap = valueList.get(0);
                return RemoteFileMetadata.fromDict(fileMetadataMap);

            } catch (Exception e) {
                throw new RuntimeException("Failed to upload file: " + e.getMessage() + " API URL: " + apiUrl, e);
            }
        }
    }

    /**
     * Implementation of the FileDownloader interface that downloads files from Azure Container Apps.
     */
    public class FileDownloaderImpl implements FileDownloader {
        @Override
        public String downloadFile(String remoteFilePath) {
            String accessToken = getAccessToken();
            String apiUrl = buildUrl("files/content/" + remoteFilePath);
            System.out.println("Downloading: API URL:" + apiUrl);

            HttpRequest request = HttpRequest.builder()
                    .method(HttpMethod.GET)
                    .url(apiUrl)
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .build();

            try {
                SuccessfulHttpResponse response = langchainHttpClient.execute(request);

                if (response.statusCode() == 404) {
                    return "File not found: " + remoteFilePath;
                }

                // Convert response body to Base64
                byte[] fileBytes = response.body().getBytes(StandardCharsets.UTF_8);
                return Base64.getEncoder().encodeToString(fileBytes);

            } catch (Exception e) {
                if (e.getMessage().contains("404")) {
                    return "File not found: " + remoteFilePath;
                }
                throw new RuntimeException("Failed to download file: " + e.getMessage() + " API URL: " + apiUrl, e);
            }
        }
    }

    /**
     * Implementation of the FileLister interface that lists files in Azure Container Apps.
     */
    public class FileListerImpl implements FileLister {
        @Override
        public String listFiles() {
            String accessToken = getAccessToken();
            String apiUrl = buildUrl("files");

            HttpRequest request = HttpRequest.builder()
                    .method(HttpMethod.GET)
                    .url(apiUrl)
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .build();

            try {
                SuccessfulHttpResponse response = langchainHttpClient.execute(request);

                // Parse the response body as JSON using Jackson
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode json = objectMapper.readTree(response.body());

                // Create a StringBuilder to store the filenames
                StringBuilder filenames = new StringBuilder();

                // Get the "value" array from the JSON object
                JsonNode valueArray = json.get("value");

                // Check if the "value" array is empty
                if (valueArray.isEmpty()) {
                    return "No files were found at " + apiUrl;
                }

                // Loop through each object in the "value" array
                for (int i = 0; i < valueArray.size(); i++) {
                    // Get the current object
                    JsonNode currentObject = valueArray.get(i);

                    // Get the "properties" object from the current object
                    JsonNode properties = currentObject.get("properties");

                    // Get the filename from the "properties" object
                    String filename = properties.get("filename").asText();

                    // Append the filename to the StringBuilder
                    filenames.append(filename);

                    // If this is not the last filename, append a comma and a space
                    if (i < valueArray.size() - 1) {
                        filenames.append(", ");
                    }
                }

                // Return the string representation of the StringBuilder
                return filenames.toString();

            } catch (Exception e) {
                throw new RuntimeException("Failed to list files: " + e.getMessage() + " API URL: " + apiUrl, e);
            }
        }
    }

    /**
     * Represents metadata of a remote file.
     */
    public static class RemoteFileMetadata {
        private final String filename;
        private final long sizeInBytes;

        /**
         * Constructs a RemoteFileMetadata instance.
         *
         * @param filename the name of the file
         * @param sizeInBytes the size of the file in bytes
         */
        public RemoteFileMetadata(String filename, long sizeInBytes) {
            this.filename = filename;
            this.sizeInBytes = sizeInBytes;
        }

        /**
         * Gets the filename.
         *
         * @return the filename
         */
        public String getFilename() {
            return filename;
        }

        /**
         * Gets the size of the file in bytes.
         *
         * @return the size in bytes
         */
        public long getSizeInBytes() {
            return sizeInBytes;
        }

        /**
         * Gets the full path of the file.
         *
         * @return the full file path
         */
        public String getFullPath() {
            return "/mnt/data/" + filename;
        }

        /**
         * Creates a RemoteFileMetadata instance from a map.
         *
         * @param data the map containing file properties
         * @return a RemoteFileMetadata instance
         */
        public static RemoteFileMetadata fromDict(Map<String, Object> data) {
            Map<String, Object> properties = (Map<String, Object>) data.get("properties");
            String filename = (String) properties.get("filename");
            Number size = (Number) properties.get("size");
            return new RemoteFileMetadata(filename, size.longValue());
        }
    }
}
