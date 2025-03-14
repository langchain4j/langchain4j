package dev.langchain4j.azure.aca.dynamicsessions;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import okhttp3.*;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * A tool for executing code in Azure ACA dynamic sessions.
 * See the examples here for more information:
 * https://github.com/langchain4j/langchain4j-examples/tree/main/azure-aca-dynamic-sessions-examples
 *
 * Overview:
 * SessionsREPLTool provides a mechanism to execute code snippets within an Azure
 * Container Apps (ACA) dynamic session. It facilitates interaction with a remote code execution environment,
 * allowing for tasks such as performing calculations, running scripts, and managing files.
 *
 * Key Components:
 *   USER_AGENT:  A static string defining the User-Agent header for HTTP requests.
 *   API_VERSION:  A static string specifying the API version for interacting with the ACA dynamic sessions endpoint.
 *   SANITIZE_PATTERN_START and SANITIZE_PATTERN_END:  Static patterns used to sanitize input code by removing leading/trailing whitespace or keywords.
 *   name:  The name of the tool (sessions_REPL), used for identification and invocation.
 *   description:  A description of the tool, explaining its purpose and usage.
 *   sanitizeInput:  A boolean flag indicating whether to sanitize the input code before execution.
 *   poolManagementEndpoint:  The URL of the pool management endpoint for the ACA dynamic sessions.
 *   sessionId:  The unique identifier for the ACA dynamic session.
 *   httpClient:  An HttpClient instance for making HTTP requests.
 *   credential:  A DefaultAzureCredential instance for authenticating with Azure services.
 *   accessTokenRef:  An AtomicReference to store and manage the access token.
 *   client:  An OkHttpClient instance for performing file operations (upload, download, list).
 *   FileUploader, FileDownloader, FileLister:  Interfaces defining file management operations.
 *   MyFileUploader, MyFileDownloader, MyFileLister:  Implementations of the file management interfaces.
 *   RemoteFileMetadata:  A class representing metadata for remote files.
 *
 * Functionality:
 * The SessionsREPLTool class provides the following core functionalities:
 *   Code Execution:  Executes code snippets within the ACA dynamic session and returns the results.
 *   File Management:  Allows uploading, downloading, and listing files within the session's environment.
 *   Authentication:  Handles authentication with Azure services using the DefaultAzureCredential.
 *   Input Sanitization:  Provides an option to sanitize input code for security and compatibility.
 *
 * Usage:
 * To use the SessionsREPLTool, you need to:
 *  Create an instance of the class, providing the pool management endpoint.
 *  Call the use() method to execute code, passing the code snippet as a string.
 *  Use the file management interfaces and implementations to interact with files in the session.
 *  Ensure proper error handling and resource management.
 */
public class SessionsREPLTool {

    private static final String USER_AGENT = "langchain4j-azure-dynamic-sessions/1.0.0-beta1 (Language=Java)";
    private static final String API_VERSION = "2024-02-02-preview";

    private static final Pattern SANITIZE_PATTERN_START = Pattern.compile("^(\\s|`)*(?i:python)?\\s*");
    private static final Pattern SANITIZE_PATTERN_END = Pattern.compile("(\\s|`)*$");

    private final String name = "sessions_REPL";
    private final String description =
            "Use this to execute commands when you need to perform calculations or computations. Input should be a valid command. Returns a JSON object with the result, stdout, and stderr.";
    private final boolean sanitizeInput;
    private final String poolManagementEndpoint;
    private final String sessionId;
    private final HttpClient httpClient;
    private final DefaultAzureCredential credential;
    private final AtomicReference<AccessToken> accessTokenRef = new AtomicReference<>();

    private OkHttpClient client;

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
        RemoteFileMetadata uploadFileToAca(Path localFilePath);
    }

    /**
     * Interface for downloading files from ACA.
     */
    public interface FileDownloader {
        String downloadFile(String remoteFilePath);
    }

    /**
     * Interface for listing files in ACA.
     */
    public interface FileLister {
        String listFiles();
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
        this.httpClient = HttpClient.newBuilder().build();
        this.credential = new DefaultAzureCredentialBuilder().build();
        this.client = new OkHttpClient(); // Initialize OkHttpClient in the constructor
    }

    // Add a method to shut down the OkHttpClient
    public void shutdown() {
        if (client != null) {
            client.dispatcher().executorService().shutdown(); // close executor service
            client.connectionPool().evictAll(); // close and remove all connection
            client = null;
        }
    }

    /**
     * Executes the input code and returns the result.
     *
     * @param input the code or query to execute
     * @return the execution result as a JSON string
     */
    @Tool(name = "sessions_REPL")
    public String use(String input) {
        Map<String, Object> response = execute(input);

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
    public Map<String, Object> execute(String sessionCode) {
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

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .header("User-Agent", USER_AGENT)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

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

    public class MyFileUploader implements FileUploader {

        @Override
        public RemoteFileMetadata uploadFileToAca(Path localFilePath) {
            String remoteFilePath = localFilePath.getFileName().toString();
            System.out.println("Found file at local path: " + remoteFilePath);
            String accessToken = getAccessToken();
            String apiUrl = buildUrl("files/upload");
            System.out.println("Uploading: API URL:" + apiUrl);
            OkHttpClient client = new OkHttpClient();

            File file = localFilePath.toFile(); // Convert Path to File

            try {
                RequestBody fileBody =
                        RequestBody.create(file, MediaType.parse("application/json")); // Use application/json
                RequestBody requestBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("file", file.getName(), fileBody)
                        .build();

                Request request = new Request.Builder()
                        .url(apiUrl)
                        .post(requestBody)
                        .addHeader("Authorization", "Bearer " + accessToken)
                        .addHeader("Content-Type", "multipart/form-data")
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        throw new IOException("Unexpected code " + response);
                    }

                    String responseBody = response.body().string();
                    ObjectMapper objectMapper = new ObjectMapper();
                    Map<String, Object> responseJson = objectMapper.readValue(responseBody, Map.class);
                    List<Map<String, Object>> valueList = (List<Map<String, Object>>) responseJson.get("value");
                    Map<String, Object> fileMetadataMap = valueList.get(0);
                    return RemoteFileMetadata.fromDict(fileMetadataMap);
                }

            } catch (Exception e) {
                throw new RuntimeException("Failed to upload file: " + e.getMessage() + " API URL: " + apiUrl, e);
            }
        }
    }

    public class MyFileDownloader implements FileDownloader {
        @Override
        public String downloadFile(String remoteFilePath) {
            String accessToken = getAccessToken();
            String apiUrl = buildUrl("files/content/" + remoteFilePath);
            System.out.println("Downloading: API URL:" + apiUrl);

            OkHttpClient client = new OkHttpClient();

            Request request = new Request.Builder()
                    .url(apiUrl)
                    .get()
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    if (response.code() == 404) {
                        return "File not found: " + remoteFilePath;
                    }
                    throw new IOException("Unexpected code " + response);
                }

                byte[] fileBytes = response.body().bytes();
                return Base64.getEncoder().encodeToString(fileBytes);

            } catch (Exception e) {
                throw new RuntimeException("Failed to download file: " + e.getMessage() + " API URL: " + apiUrl, e);
            }
        }
    }

    public class MyFileLister implements FileLister {
        @Override
        public String listFiles() {
            String accessToken = getAccessToken();
            String apiUrl = buildUrl("files");

            OkHttpClient client = new OkHttpClient();

            Request request = new Request.Builder()
                    .url(apiUrl)
                    .get()
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Unexpected code " + response);
                }

                // Parse the response body as JSON
                JSONObject json = new JSONObject(response.body().string());

                // Create a StringBuilder to store the filenames
                StringBuilder filenames = new StringBuilder();

                // Get the "value" array from the JSON object
                JSONArray valueArray = json.getJSONArray("value");

                // Check if the "value" array is empty
                if (valueArray.length() == 0) {
                    return "No files were found at " + apiUrl;
                }

                // Loop through each object in the "value" array
                for (int i = 0; i < valueArray.length(); i++) {
                    // Get the current object
                    JSONObject currentObject = valueArray.getJSONObject(i);

                    // Get the "properties" object from the current object
                    JSONObject properties = currentObject.getJSONObject("properties");

                    // Get the filename from the "properties" object
                    String filename = properties.getString("filename");

                    // Append the filename to the StringBuilder
                    filenames.append(filename);

                    // If this is not the last filename, append a comma and a space
                    if (i < valueArray.length() - 1) {
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
