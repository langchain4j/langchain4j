package dev.langchain4j.model.openai.internal.image;

import dev.langchain4j.data.image.Image;
import java.util.Base64;

/**
 * A single image (or mask) to be sent as a multipart part to OpenAI's image-edit endpoint.
 * Carries the decoded raw bytes plus the filename and MIME type used for the
 * {@code Content-Disposition} part header.
 */
public class EditImageFile {

    private static final String DEFAULT_MIME_TYPE = "image/png";

    private final byte[] content;
    private final String fileName;
    private final String mimeType;

    public EditImageFile(byte[] content, String fileName, String mimeType) {
        this.content = content;
        this.fileName = fileName;
        this.mimeType = mimeType;
    }

    public byte[] content() {
        return content;
    }

    public String fileName() {
        return fileName;
    }

    public String mimeType() {
        return mimeType;
    }

    /**
     * Build an {@link EditImageFile} from an {@link Image}. The image must carry
     * {@link Image#base64Data()}; URL-only images are rejected because fetching the URL
     * is out of scope for this provider (caller should fetch and encode upstream).
     *
     * @param image    the source image
     * @param fileName multipart filename (used in {@code Content-Disposition})
     */
    public static EditImageFile from(Image image, String fileName) {
        if (image == null) {
            throw new IllegalArgumentException("image must not be null");
        }
        if (image.base64Data() == null) {
            if (image.url() != null) {
                throw new IllegalArgumentException(
                        "OpenAI image edit requires Image.base64Data(); URL images are not supported. "
                                + "Fetch and encode the image before calling edit().");
            }
            throw new IllegalArgumentException(
                    "Image has neither base64Data nor url; nothing to send to OpenAI image edit.");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(image.base64Data());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid base64 image data provided", e);
        }
        String mimeType = image.mimeType() != null ? image.mimeType() : DEFAULT_MIME_TYPE;
        return new EditImageFile(decoded, fileName, mimeType);
    }
}
