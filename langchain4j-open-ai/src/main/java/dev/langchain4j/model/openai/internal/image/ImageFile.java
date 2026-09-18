package dev.langchain4j.model.openai.internal.image;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.image.Image;
import java.util.Base64;

/**
 * Represents an image to be uploaded to the OpenAI image edit API as multipart form data.
 */
public class ImageFile {

    private final Image image;

    private ImageFile(Image image) {
        this.image = ensureNotNull(image, "image");
    }

    public String fileName() {
        return "image" + getImageExtension(image.mimeType());
    }

    public String mimeType() {
        return image.mimeType() != null ? image.mimeType() : "image/png";
    }

    public byte[] content() {
        if (image.base64Data() != null) {
            try {
                return Base64.getDecoder().decode(image.base64Data());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid base64 image data provided", e);
            }
        }

        if (image.url() != null) {
            throw new IllegalArgumentException(
                    "URL-based image is not supported by OpenAI image editing. Please provide the image as base64 encoded data.");
        }

        throw new IllegalArgumentException("No image data found. Image must contain base64 data");
    }

    public static ImageFile from(Image image) {
        return new ImageFile(image);
    }

    private static String getImageExtension(String mimeType) {
        if (mimeType == null) {
            return ".png";
        }

        return switch (mimeType) {
            case "image/png" -> ".png";
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/webp" -> ".webp";
            default -> ".png";
        };
    }
}
