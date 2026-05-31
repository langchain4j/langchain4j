package dev.langchain4j.model.google.genai;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import com.google.genai.Client;
import com.google.genai.types.DeleteFileConfig;
import com.google.genai.types.File;
import com.google.genai.types.GetFileConfig;
import com.google.genai.types.ListFilesConfig;
import com.google.genai.types.UploadFileConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for uploading and managing media files with Google AI Gemini using the official com.google.genai SDK.
 *
 * <p>
 * The Gemini models support multimodal inputs including text, images, audio, videos, and documents.
 * Use this API to upload media files when the total request size exceeds 20 MB.
 *
 * <p>
 * Files are stored for 48 hours and can be referenced in content generation requests using their URI.
 * The API supports up to 20 GB of files per project, with a maximum of 2 GB per individual file.
 */
public final class GoogleGenAiFiles {

    private final Client client;

    private GoogleGenAiFiles(Builder builder) {
        this.client = builder.client != null
                ? builder.client
                : GoogleGenAiClientFactory.createClient(
                        builder.apiKey, null, null, null, null, builder.customHeaders, builder.apiEndpoint);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Uploads a file to Gemini.
     *
     * <p><strong>Note:</strong> The Files API lets you store up to 20 GB of files per project, with a per-file
     * maximum size of 2 GB. Files are stored for 48 hours.
     *
     * @param filePath    path to the file to upload
     * @param displayName optional display name for the file
     */
    public File uploadFile(Path filePath, String displayName) {
        ensureNotNull(filePath, "filePath");
        try {
            UploadFileConfig config = UploadFileConfig.builder()
                    .displayName(
                            displayName != null
                                    ? displayName
                                    : filePath.getFileName().toString())
                    .mimeType(detectMimeType(filePath))
                    .build();
            return client.files.upload(filePath.toFile(), config);
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload file", e);
        }
    }

    /**
     * Uploads a file to Gemini.
     *
     * <p><strong>Note:</strong> The Files API lets you store up to 20 GB of files per project, with a per-file
     * maximum size of 2 GB. Files are stored for 48 hours.
     *
     * @param fileBytes byte array that is the file to be uploaded
     * @param mimeType  mimetype of the file that is being uploaded
     * @param name      optional display name for the file
     */
    public File uploadFile(byte[] fileBytes, String mimeType, String name) {
        ensureNotNull(fileBytes, "fileBytes");
        ensureNotNull(mimeType, "mimeType");
        ensureNotNull(name, "name");

        UploadFileConfig config =
                UploadFileConfig.builder().displayName(name).mimeType(mimeType).build();
        return client.files.upload(fileBytes, config);
    }

    /**
     * Retrieves metadata for a specific uploaded file.
     *
     * @param name the name of the file to retrieve metadata for (e.g., "files/abc123")
     */
    public File getMetadata(String name) {
        ensureNotBlank(name, "name");
        return client.files.get(name, GetFileConfig.builder().build());
    }

    /**
     * Lists all uploaded files. Returns a list of uploaded files.
     *
     * <p><strong>Note:</strong> The Files API lets you store up to 20 GB of files per project, with a per-file
     * maximum size of 2 GB. Files are stored for 48 hours.
     */
    public List<File> listFiles() {
        List<File> allFiles = new ArrayList<>();
        client.files.list(ListFilesConfig.builder().build()).forEach(allFiles::add);
        return allFiles;
    }

    /**
     * Deletes an uploaded file by name.
     *
     * @param name the name of the file to delete (e.g., "files/abc123")
     */
    public void deleteFile(String name) {
        ensureNotBlank(name, "name");
        client.files.delete(name, DeleteFileConfig.builder().build());
    }

    /**
     * Detects the MIME type of a file.
     */
    private String detectMimeType(Path filePath) throws IOException {
        String mimeType = Files.probeContentType(filePath);
        if (mimeType == null) {
            try {
                mimeType = GoogleGenAiContentMapper.detectMimeType(filePath.toUri());
            } catch (IllegalArgumentException e) {
                // Fallback to application/octet-stream if MIME type cannot be detected
                mimeType = "application/octet-stream";
            }
        }
        return mimeType;
    }

    public static class Builder {
        private String apiKey;
        private String apiEndpoint;
        private java.util.Map<String, String> customHeaders;
        private Client client;

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder apiEndpoint(String apiEndpoint) {
            this.apiEndpoint = apiEndpoint;
            return this;
        }

        public Builder customHeaders(java.util.Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        public Builder client(Client client) {
            this.client = client;
            return this;
        }

        public GoogleGenAiFiles build() {
            return new GoogleGenAiFiles(this);
        }
    }
}
