package dev.langchain4j.model.onnx.genai;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for downloading ONNX GenAI models from Hugging Face.
 * This utility is used in integration tests to download models on-demand
 * instead of storing large model files in the repository.
 */
public class ModelDownloadUtil {

    private static final Logger logger = LoggerFactory.getLogger(ModelDownloadUtil.class);

    // Default model configuration
    private static final String DEFAULT_HF_REPO = "asoldano/Llama-3.2-1B-Instruct-INT4-onnx";
    private static final String DEFAULT_MODEL_NAME = "Llama-3.2-1B-Instruct-INT4-onnx";

    // Model files that need to be downloaded
    private static final List<String> MODEL_FILES = List.of(
            "model.onnx",
            "model.onnx.data",
            "genai_config.json",
            "tokenizer.json",
            "tokenizer_config.json",
            "special_tokens_map.json",
            "chat_template.jinja");

    /**
     * Downloads the default model to the test-classes directory if it doesn't exist.
     *
     * @return Path to the downloaded model directory
     * @throws IOException if download fails
     */
    public static String ensureModelDownloaded() throws IOException {
        return ensureModelDownloaded(DEFAULT_HF_REPO, DEFAULT_MODEL_NAME);
    }

    /**
     * Downloads a model from Hugging Face to the test-classes directory if it doesn't exist.
     *
     * @param huggingFaceRepo The Hugging Face repository (e.g., "microsoft/Llama-3.2-1B-Instruct-INT4")
     * @param modelName The local model directory name
     * @return Path to the downloaded model directory
     * @throws IOException if download fails
     */
    public static String ensureModelDownloaded(String huggingFaceRepo, String modelName) throws IOException {
        Path testClassesDir = Paths.get("target/test-classes");
        Path modelDir = testClassesDir.resolve(modelName);

        if (isModelComplete(modelDir)) {
            logger.info("Model {} already exists at {}", modelName, modelDir.toAbsolutePath());
            return modelDir.toString();
        }

        logger.info("Downloading model {} from Hugging Face repository {}", modelName, huggingFaceRepo);

        Files.createDirectories(modelDir);

        for (String fileName : MODEL_FILES) {
            downloadFile(huggingFaceRepo, fileName, modelDir.resolve(fileName));
        }

        logger.info("Model download completed: {}", modelDir.toAbsolutePath());
        return modelDir.toString();
    }

    /**
     * Checks if all required model files exist in the model directory.
     *
     * @param modelDir The model directory
     * @return true if all files exist, false otherwise
     */
    private static boolean isModelComplete(Path modelDir) {
        if (!Files.exists(modelDir)) {
            return false;
        }

        for (String fileName : MODEL_FILES) {
            Path filePath = modelDir.resolve(fileName);
            if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
                logger.debug("Missing or unreadable file: {}", filePath);
                return false;
            }
        }

        return true;
    }

    /**
     * Downloads a single file from Hugging Face.
     *
     * @param huggingFaceRepo The Hugging Face repository
     * @param fileName The file name to download
     * @param targetPath The target path to save the file
     * @throws IOException if download fails
     */
    private static void downloadFile(String huggingFaceRepo, String fileName, Path targetPath) throws IOException {
        String url = String.format("https://huggingface.co/%s/resolve/main/%s", huggingFaceRepo, fileName);

        logger.debug("Downloading {} from {}", fileName, url);

        HttpURLConnection connection = null;
        try {
            URL downloadUrl = new URL(url);
            connection = (HttpURLConnection) downloadUrl.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(30000); // 30 seconds
            connection.setReadTimeout(300000); // 5 minutes

            // Add User-Agent header to avoid potential blocking
            connection.setRequestProperty("User-Agent", "langchain4j-onnx-genai/1.0");

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException(String.format(
                        "Failed to download %s: HTTP %d - %s",
                        fileName, responseCode, connection.getResponseMessage()));
            }

            // Get file size for progress tracking
            long fileSize = connection.getContentLengthLong();
            if (fileSize > 0) {
                logger.info("Downloading {} ({} MB)", fileName, fileSize / (1024 * 1024));
            }

            // Download file with progress tracking
            try (InputStream in = connection.getInputStream()) {
                Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }

            logger.debug("Successfully downloaded {}", fileName);

        } catch (IOException e) {
            // Clean up partial download
            try {
                Files.deleteIfExists(targetPath);
            } catch (IOException cleanupException) {
                logger.warn("Failed to clean up partial download: {}", targetPath, cleanupException);
            }
            throw new IOException("Failed to download " + fileName + " from " + url, e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Cleans up downloaded models (useful for testing).
     *
     * @param modelName The model name to clean up
     */
    public static void cleanupModel(String modelName) {
        Path modelDir = Paths.get("target/test-classes").resolve(modelName);
        try {
            if (Files.exists(modelDir)) {
                Files.walk(modelDir)
                        .sorted((a, b) -> b.compareTo(a)) // Delete files before directories
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                logger.warn("Failed to delete {}", path, e);
                            }
                        });
                logger.info("Cleaned up model directory: {}", modelDir);
            }
        } catch (IOException e) {
            logger.warn("Failed to cleanup model directory: {}", modelDir, e);
        }
    }
}
