package dev.langchain4j.code.azure.acads;

import static dev.langchain4j.http.client.HttpClientBuilderLoader.loadHttpClientBuilder;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.code.CodeExecutionEngine;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpMethod;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A tool for executing code in Azure ACA dynamic sessions.
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
 *   DefaultFileUploader, DefaultFileDownloader, DefaultFileLister:  Implementations of the file management interfaces.
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

    private static final String USER_AGENT = "langchain4j-azure-dynamic-sessions";
    private static final String API_VERSION = "2024-02-02-preview";
    private static final Logger log = LoggerFactory.getLogger(SessionsREPLTool.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final TypeReference<Map<String, Object>> MAP_TYPE_REF = new TypeReference<Map<String, Object>>() {};
    private static final TypeReference<List<Map<String, Object>>> LIST_MAP_TYPE_REF =
            new TypeReference<List<Map<String, Object>>>() {};

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
        this.langchainHttpClient = loadHttpClientBuilder().build();
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
    // public void shutdown() {
    // No manual cleanup needed for the HTTP client
    // }

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
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(contentMap);
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
            String requestBody = OBJECT_MAPPER.writeValueAsString(body);

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
                Map<String, Object> responseJson = OBJECT_MAPPER.readValue(response.body(), MAP_TYPE_REF);
                return getNestedMapProperty(responseJson, "properties");
            } else {
                throw new RuntimeException(
                        "Request failed with status code " + response.statusCode() + ": " + response.body());
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to execute code.", e);
        }
    }

    /**
     * Default implementation of the FileUploader interface that uploads files to Azure Container Apps.
     */
    public class DefaultFileUploader implements FileUploader {

        /**
         * Constructs a new DefaultFileUploader.
         * Uses the parent SessionsREPLTool's configuration for authentication and endpoint access.
         */
        public DefaultFileUploader() {
            // Default constructor - uses parent SessionsREPLTool's configuration
        }

        @Override
        public RemoteFileMetadata uploadFileToAca(Path localFilePath) {
            String remoteFilePath = localFilePath.getFileName().toString();
            log.debug("Found file at local path: " + remoteFilePath);
            String accessToken = getAccessToken();
            String apiUrl = buildUrl("files/upload");
            log.debug("Uploading: API URL:" + apiUrl);

            File file = localFilePath.toFile(); // Convert Path to File

            try {
                // Prepare multipart form data

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

                Map<String, Object> responseJson = OBJECT_MAPPER.readValue(response.body(), MAP_TYPE_REF);
                List<Map<String, Object>> valueList =
                        OBJECT_MAPPER.convertValue(responseJson.get("value"), LIST_MAP_TYPE_REF);
                Map<String, Object> fileMetadataMap = valueList.get(0);
                return RemoteFileMetadata.fromDict(fileMetadataMap);

            } catch (Exception e) {
                throw new RuntimeException("Failed to upload file: " + e.getMessage() + " API URL: " + apiUrl, e);
            }
        }
    }

    /**
     * Default implementation of the FileDownloader interface that downloads files from Azure Container Apps.
     */
    public class DefaultFileDownloader implements FileDownloader {

        /**
         * Constructs a new DefaultFileDownloader.
         * Uses the parent SessionsREPLTool's configuration for authentication and endpoint access.
         */
        public DefaultFileDownloader() {
            // Default constructor - uses parent SessionsREPLTool's configuration
        }

        @Override
        public String downloadFile(String remoteFilePath) {
            String accessToken = getAccessToken();
            String apiUrl = buildUrl("files/content/" + remoteFilePath);
            log.debug("Downloading: API URL:" + apiUrl);

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
     * Default implementation of the FileLister interface that lists files in Azure Container Apps.
     */
    public class DefaultFileLister implements FileLister {

        /**
         * Constructs a new DefaultFileLister.
         * Uses the parent SessionsREPLTool's configuration for authentication and endpoint access.
         */
        public DefaultFileLister() {
            // Default constructor - uses parent SessionsREPLTool's configuration
        }

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
                JsonNode json = OBJECT_MAPPER.readTree(response.body());

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

    private static Map<String, Object> getNestedMapProperty(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map) {
            // This is safe because we know the structure from Azure API
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) value;
            return result;
        }
        return null;
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
            Object propertiesObj = data.get("properties");
            if (!(propertiesObj instanceof Map)) {
                throw new IllegalArgumentException("Data does not contain a valid properties map");
            }

            // This cast is safe because we checked the type above
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) propertiesObj;

            String filename = (String) properties.get("filename");
            Number size = (Number) properties.get("size");
            return new RemoteFileMetadata(filename, size.longValue());
        }
    }
}
