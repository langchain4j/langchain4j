package dev.langchain4j.model.bedrock;

import static java.util.Objects.isNull;

import dev.langchain4j.Internal;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.exception.UnsupportedFeatureException;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import software.amazon.awssdk.services.bedrockruntime.model.ImageFormat;

/**
 * Utility methods.
 */
@Internal
class Utils {
    /**
     * Extracts the extension from a file path, URI, or URL.
     * @param uri The path to analyze.
     * @return The file extension (without the dot) or an empty string if there is no extension.
     */
    public static String extractExtension(URI uri) {
        final String path = uri.getPath();
        if (isNull(path) || path.isEmpty()) {
            return "";
        }

        String cleanPath = path.split("\\?")[0];
        cleanPath = cleanPath.split("#")[0];

        String fileName = cleanPath;

        int lastSlash = Math.max(cleanPath.lastIndexOf('/'), cleanPath.lastIndexOf('\\'));
        if (lastSlash >= 0) {
            fileName = cleanPath.substring(lastSlash + 1);
        }

        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) { // > 0 ignore filenames with a . at beginning
            return fileName.substring(lastDot + 1);
        }

        return "";
    }

    private static final Map<String, ImageFormat> MIME_TYPE_MAPPING = Map.of(
            "image/png", ImageFormat.PNG,
            "image/jpeg", ImageFormat.JPEG,
            "image/jpg", ImageFormat.JPEG,
            "image/gif", ImageFormat.GIF,
            "image/webp", ImageFormat.WEBP);

    private static final Map<String, ImageFormat> EXTENSION_MAPPING = Map.of(
            "png", ImageFormat.PNG,
            "jpg", ImageFormat.JPEG,
            "jpeg", ImageFormat.JPEG,
            "gif", ImageFormat.GIF,
            "webp", ImageFormat.WEBP);

    private static final Set<ImageFormat> SUPPORTED_FORMATS =
            Set.of(ImageFormat.PNG, ImageFormat.JPEG, ImageFormat.GIF, ImageFormat.WEBP);

    /**
     * Extracts the image format from an Image object using either mime type or URI.
     * Prioritizes mime type over file extension.
     *
     * https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_ImageBlock.html
     * imgFormat valid values are : png | jpeg | gif | webp
     *
     * @param image The Image object containing mime type and URI
     * @return The normalized ImageFormat
     * @throws dev.langchain4j.exception.UnsupportedFeatureException if the format is not supported
     * @throws IllegalArgumentException if the image is null
     */
    public static String extractAndValidateFormat(Image image) {
        if (image == null) {
            throw new IllegalArgumentException("Image cannot be null");
        }

        // First try to extract from mime type
        if (image.mimeType() != null && !image.mimeType().isBlank()) {
            ImageFormat format = MIME_TYPE_MAPPING.get(image.mimeType().toLowerCase());
            if (format != null) {
                return format.toString();
            }
        }

        // If mime type fails, try to extract from URI
        if (image.url() != null) {
            String extension = Utils.extractExtension(image.url()).toLowerCase();
            ImageFormat format = EXTENSION_MAPPING.get(extension);
            if (format != null) {
                return format.toString();
            }
        }

        throw new UnsupportedFeatureException(String.format(
                "Unsupported image format, should be one of png | jpeg | gif | webp. Mime type: %s, URI: %s",
                image.mimeType(), image.url()));
    }

    /**
     * Extracts and sanitizes a filename from a URI.
     * Allowed characters are:
     * - Alphanumeric characters
     * - Single whitespace (multiple spaces are reduced to one)
     * - Hyphens
     * - Parentheses
     * - Square brackets
     *
     * @param uri The URI to process
     * @return The cleaned filename or empty string if no filename is found
     */
    public static String extractCleanFileName(URI uri) {
        if (uri == null) {
            return "";
        }

        // Get the path and extract the filename
        String path = uri.getPath();
        if (isNull(path) || path.isEmpty()) {
            return "";
        }

        // Remove query parameters and fragments
        String cleanPath = path.split("\\?")[0];
        cleanPath = cleanPath.split("#")[0];

        // Extract filename from path
        int lastSlash = Math.max(cleanPath.lastIndexOf('/'), cleanPath.lastIndexOf('\\'));
        String fileName = (lastSlash >= 0) ? cleanPath.substring(lastSlash + 1) : cleanPath;

        // If filename is empty or just dots, return empty string
        if (fileName.isEmpty() || fileName.matches("^[.]+$")) {
            return "";
        }

        // Remove the extension (everything after the last dot, if not at start)
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) { // > 0 to keep files starting with a dot
            fileName = fileName.substring(0, lastDot);
        }

        // Clean the filename according to rules
        return fileName
                // Replace any character that's not allowed with a space
                .replaceAll("[^a-zA-Z0-9\\s\\-()\\[\\]]", "-")
                // Replace multiple spaces with a single space
                .replaceAll("\\s+", "-")
                // Trim spaces at start and end
                .trim();
    }
}
