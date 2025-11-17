package dev.langchain4j.model.googleai;

import static dev.langchain4j.internal.Utils.firstNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.model.googleai.Json.fromJson;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
final class GeminiFiles {
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

    GeminiFiles(String apiKey, @Nullable HttpClient httpClient, @Nullable String baseUrl) {
        this.apiKey = ensureNotBlank(apiKey, "apiKey");
        this.httpClient = firstNotNull("httpClient", httpClient, HttpClient.newHttpClient());
        this.baseUrl = firstNotNull("baseUrl", baseUrl, BASE_URL);
    }

    /**
     * Uploads a file to Gemini using the resumable upload protocol.
     *
     * @param filePath    path to the file to upload
     * @param displayName optional display name for the file
     * @return the uploaded file information including the file URI
     * @throws IOException if file cannot be read
     */
    GeminiFile uploadFile(Path filePath, @Nullable String displayName) throws IOException, InterruptedException {
        ensureNotNull(filePath, "filePath");

        byte[] fileBytes = Files.readAllBytes(filePath);
        String mimeType = detectMimeType(filePath);
        String name = displayName != null ? displayName : filePath.getFileName().toString();

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
     * @return the metadata of the specified file
     * @throws IOException          if an error occurs during the request
     * @throws InterruptedException if the request is interrupted
     */
    GeminiFile getMetadata(String name) throws IOException, InterruptedException {
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
     * Lists all uploaded files.
     *
     * @return a list of uploaded files
     * @throws IOException          if an error occurs during the request
     * @throws InterruptedException if the request is interrupted
     */
    List<GeminiFile> listFiles() throws IOException, InterruptedException {
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
     * @throws IOException          if an error occurs during the request
     * @throws InterruptedException if the request is interrupted
     */
    void deleteFile(String name) throws IOException, InterruptedException {
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
    private String initiateResumableUpload(long contentLength, String mimeType, String displayName) throws InterruptedException {
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
            if (uploadUrlList.isEmpty() || uploadUrlList.get(0) == null || uploadUrlList.get(0).isEmpty()) {
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
        record FileInfo(String display_name) {
        }
    }

    /**
     * Response from the file upload containing file information.
     */
    record GeminiFileResponse(GeminiFile file) {
    }

    /**
     * Response from listing files.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeminiFilesListResponse(List<GeminiFile> files) {
    }

    static class GeminiUploadFailureException extends RuntimeException {
        GeminiUploadFailureException(String message, Throwable cause) {
            super(message, cause);
        }

        GeminiUploadFailureException(String message) {
            super(message);
        }
    }

    /**
     * Represents a file uploaded to Google AI Gemini.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeminiFile(
            String name,
            @Nullable String displayName,
            String mimeType,
            Long sizeBytes,
            String createTime,
            String updateTime,
            String expirationTime,
            String sha256Hash,
            String uri,
            String state
    ) {
        /**
         * Returns whether the file is in ACTIVE state and ready to use.
         */
        boolean isActive() {
            return "ACTIVE".equals(state);
        }

        /**
         * Returns whether the file is still processing.
         */
        boolean isProcessing() {
            return "PROCESSING".equals(state);
        }

        /**
         * Returns whether the file failed to process.
         */
        boolean isFailed() {
            return "FAILED".equals(state);
        }
    }
}
