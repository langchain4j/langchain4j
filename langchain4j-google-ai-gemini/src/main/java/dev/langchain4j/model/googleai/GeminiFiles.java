package dev.langchain4j.model.googleai;

import static dev.langchain4j.internal.Utils.firstNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.model.googleai.Json.fromJson;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Service for uploading and managing media files with Google AI Gemini.
 *
 * <p>
 * The Gemini models support multimodal inputs including text, images, audio, videos, and documents.
 * Use this API to upload media files when the total request size exceeds 20 MB.
 *
 * <p>
 * Files are stored for 48 hours and can be referenced in content generation requests using their URI.
 * The API supports up to 20 GB of files per project, with a maximum of 2 GB per individual file.
 * During the retention period, you can retrieve file metadata but cannot download the files directly.
 */
public final class GeminiFiles {
    private static final String BASE_URL = "https://generativelanguage.googleapis.com";
    private static final String DELETE_FILE_PATH = "/v1beta";
    private static final String GET_FILE_PATH = "/v1beta";
    private static final String LIST_FILES_PATH = "/v1beta/files";
    private static final String UPLOAD_PATH = "/upload/v1beta/files";
    private static final String API_KEY_HEADER_NAME = "x-goog-api-key";
    private static final String UPLOAD_URL_HEADER = "x-goog-upload-url";

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String apiKey;

    private GeminiFiles(Builder builder) {
        this.apiKey = ensureNotBlank(builder.apiKey, "apiKey");
        this.httpClient = firstNotNull("httpClient", builder.httpClient, HttpClient.newHttpClient());
        this.baseUrl = firstNotNull("baseUrl", builder.baseUrl, BASE_URL);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Uploads a file to Gemini using the resumable upload protocol.
     *
     * <p><strong>Note:</strong> The Files API lets you store up to 20 GB of files per project, with a per-file
     * maximum size of 2 GB. Files are stored for 48 hours.
     *
     * @param filePath    path to the file to upload
     * @param displayName optional display name for the file
     */
    public GeminiFile uploadFile(Path filePath, @Nullable String displayName) throws IOException, InterruptedException {
        ensureNotNull(filePath, "filePath");
        return uploadFile(
                Files.readAllBytes(filePath),
                detectMimeType(filePath),
                displayName != null ? displayName : filePath.getFileName().toString());
    }

    /**
     * Uploads a file to Gemini using the resumable upload protocol.
     *
     * <p><strong>Note:</strong> The Files API lets you store up to 20 GB of files per project, with a per-file
     * maximum size of 2 GB. Files are stored for 48 hours.
     *
     * @param fileBytes byte array that is the file to be uploaded
     * @param mimeType  mimetype of the file that is being uploaded
     * @param name      optional display name for the file
     */
    public GeminiFile uploadFile(byte[] fileBytes, String mimeType, String name)
            throws IOException, InterruptedException {
        ensureNotNull(fileBytes, "fileBytes");
        ensureNotNull(mimeType, "mimeType");
        ensureNotNull(name, "name");

        // Step 1: Initial resumable request to get upload URL
        String uploadUrl = initiateResumableUpload(fileBytes.length, mimeType, name);

        // Step 2: Upload the actual file bytes
        GeminiFileResponse response = uploadFileBytes(uploadUrl, fileBytes);

        return response.file();
    }

    /**
     * Retrieves metadata for a specific uploaded file.
     *
     * @param name the name of the file to retrieve metadata for (e.g., "files/abc123")
     */
    public GeminiFile getMetadata(String name) throws IOException, InterruptedException {
        ensureNotBlank(name, "name");

        String url = baseUrl + GET_FILE_PATH + "/" + name;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header(API_KEY_HEADER_NAME, apiKey)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new GeminiUploadFailureException(
                    "Failed to retrieve metadata for file: " + name + ". Status code: " + response.statusCode());
        }

        return fromJson(response.body(), GeminiFile.class);
    }

    /**
     * Lists all uploaded files. Returns a list of uploaded files.
     *
     * <p><strong>Note:</strong> The Files API lets you store up to 20 GB of files per project, with a per-file
     * maximum size of 2 GB. Files are stored for 48 hours.
     */
    public List<GeminiFile> listFiles() throws IOException, InterruptedException {
        String url = baseUrl + LIST_FILES_PATH;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header(API_KEY_HEADER_NAME, apiKey)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        GeminiFilesListResponse listResponse = fromJson(response.body(), GeminiFilesListResponse.class);

        return listResponse.files() != null ? listResponse.files() : List.of();
    }

    /**
     * Deletes an uploaded file by name.
     *
     * @param name the name of the file to delete (e.g., "files/abc123")
     */
    public void deleteFile(String name) throws IOException, InterruptedException {
        ensureNotBlank(name, "name");

        String url = baseUrl + DELETE_FILE_PATH + "/" + name;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header(API_KEY_HEADER_NAME, apiKey)
                .DELETE()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (!List.of(200, 204).contains(response.statusCode())) {
            throw new GeminiUploadFailureException(
                    "Failed to delete file: " + name + ". Status code: " + response.statusCode());
        }
    }

    /**
     * Initiates a resumable upload session and returns the upload URL.
     */
    private String initiateResumableUpload(long contentLength, String mimeType, String displayName)
            throws InterruptedException {
        String url = baseUrl + UPLOAD_PATH;

        GeminiFileMetadata metadata = new GeminiFileMetadata(new GeminiFileMetadata.FileInfo(displayName));
        String jsonBody = Json.toJson(metadata);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("User-Agent", "LangChain4j")
                .header(API_KEY_HEADER_NAME, apiKey)
                .header("X-Goog-Upload-Protocol", "resumable")
                .header("X-Goog-Upload-Command", "start")
                .header("X-Goog-Upload-Header-Content-Length", String.valueOf(contentLength))
                .header("X-Goog-Upload-Header-Content-Type", mimeType)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            List<String> uploadUrlList = response.headers().allValues(UPLOAD_URL_HEADER);
            if (uploadUrlList.isEmpty()
                    || uploadUrlList.get(0) == null
                    || uploadUrlList.get(0).isEmpty()) {
                throw new IllegalStateException("Upload URL not found in response headers");
            }

            return uploadUrlList.get(0).trim();
        } catch (IOException e) {
            throw new GeminiUploadFailureException("Failed to initiate resumable upload", e);
        }
    }

    /**
     * Uploads the file bytes to the provided upload URL.
     */
    private GeminiFileResponse uploadFileBytes(String uploadUrl, byte[] fileBytes) throws InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uploadUrl))
                .header("X-Goog-Upload-Offset", "0")
                .header("X-Goog-Upload-Command", "upload, finalize")
                .POST(HttpRequest.BodyPublishers.ofByteArray(fileBytes))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return fromJson(response.body(), GeminiFileResponse.class);
        } catch (IOException e) {
            throw new GeminiUploadFailureException("Failed to upload file bytes", e);
        }
    }

    /**
     * Detects the MIME type of a file.
     */
    private String detectMimeType(Path filePath) throws IOException {
        String mimeType = Files.probeContentType(filePath);
        if (mimeType == null) {
            // Fallback to application/octet-stream if MIME type cannot be detected
            mimeType = "application/octet-stream";
        }
        return mimeType;
    }

    /**
     * Request body for initiating a resumable upload.
     */
    private record GeminiFileMetadata(FileInfo file) {
        record FileInfo(String display_name) {}
    }

    /**
     * Response from the file upload containing file information.
     */
    record GeminiFileResponse(GeminiFile file) {}

    /**
     * Response from listing files.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeminiFilesListResponse(List<GeminiFile> files) {}

    static class GeminiUploadFailureException extends RuntimeException {
        GeminiUploadFailureException(String message, Throwable cause) {
            super(message, cause);
        }

        GeminiUploadFailureException(String message) {
            super(message);
        }
    }

    public static class Builder {
        private String apiKey;
        private HttpClient httpClient;
        private String baseUrl;

        /**
         * Sets the API key for authentication.
         *
         * @param apiKey the API key (required)
         * @return this builder
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * Sets the HTTP client to use for requests.
         *
         * @param httpClient the HTTP client (optional, defaults to a new HttpClient)
         * @return this builder
         */
        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        /**
         * Sets the base URL for the API.
         *
         * @param baseUrl the base URL (optional, defaults to BASE_URL)
         * @return this builder
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * Builds a new GeminiFiles instance.
         *
         * @return a new GeminiFiles instance
         * @throws IllegalArgumentException if apiKey is blank
         */
        public GeminiFiles build() {
            return new GeminiFiles(this);
        }
    }

    /**
     * Represents a file uploaded to the Gemini API,
     * <a href="https://ai.google.dev/gemini-api/docs/files">documentation</a>
     *
     * @param name           The name of the file. This is a required field.
     * @param displayName    An optional display name for the file, which may be different from the actual file name.
     * @param mimeType       The MIME type of the file, indicating the nature and format of the file content.
     * @param sizeBytes      The size of the file in bytes.
     * @param createTime     The timestamp indicating when the file was created, formatted as an ISO-8601 string.
     * @param updateTime     The timestamp indicating when the file was last updated, formatted as an ISO 8601 string.
     * @param expirationTime The timestamp indicating when the file will expire, formatted as an ISO-8601 string.
     * @param sha256Hash     The SHA-256 hash of the file, used for integrity verification.
     * @param uri            The URI where the file can be accessed.
     * @param state          The current state of the file (e.g., active, deleted).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GeminiFile(
            String name,
            @Nullable String displayName,
            String mimeType,
            Long sizeBytes,
            String createTime,
            String updateTime,
            String expirationTime,
            String sha256Hash,
            String uri,
            String state) {
        /**
         * Returns whether the file is in ACTIVE state and ready to use.
         */
        public boolean isActive() {
            return "ACTIVE".equals(state);
        }

        /**
         * Returns whether the file is still processing.
         */
        public boolean isProcessing() {
            return "PROCESSING".equals(state);
        }

        /**
         * Returns whether the file failed to process.
         */
        public boolean isFailed() {
            return "FAILED".equals(state);
        }
    }
}
