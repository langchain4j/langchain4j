package dev.langchain4j.model.embedding.onnx.internal;

import dev.langchain4j.Internal;
import dev.langchain4j.data.image.Image;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.Base64;
import java.util.Objects;
import javax.imageio.ImageIO;

/**
 * Loads a LangChain4j {@link Image} (URL or base64) into a {@link BufferedImage}.
 */
@Internal
final class ImageFactory {
    private ImageFactory() {}

    /**
     * Load a LangChain4j {@link Image} into a {@link BufferedImage}.
     *
     * @param image a LangChain4j Image with either a URL or base64 data
     * @return the decoded BufferedImage
     * @throws IllegalArgumentException if the image has neither URL nor base64 data, or decoding fails
     * @throws UncheckedIOException     if an I/O error occurs during reading
     */
    static BufferedImage load(Image image) {
        Objects.requireNonNull(image, "Image cannot be null");

        if (image.base64Data() != null) {
            return fromBase64(image.base64Data());
        } else if (image.url() != null) {
            return fromUri(image.url());
        } else {
            throw new IllegalArgumentException("Image must have either a URL or base64 data");
        }
    }

    /**
     * Decode a base64-encoded image string into a {@link BufferedImage}.
     *
     * @param base64Data the base64-encoded image data
     * @return the decoded BufferedImage
     */
    static BufferedImage fromBase64(String base64Data) {
        try {
            byte[] bytes = Base64.getDecoder().decode(base64Data);
            BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(bytes));
            if (bufferedImage == null) {
                throw new IllegalArgumentException("Could not decode base64 image data");
            }
            return bufferedImage;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to decode base64 image", e);
        }
    }

    /**
     * Read an image from a {@link URI} into a {@link BufferedImage}.
     *
     * @param uri the URI pointing to the image (http, https, file, etc.)
     * @return the decoded BufferedImage
     */
    static BufferedImage fromUri(URI uri) {
        try {
            BufferedImage bufferedImage = ImageIO.read(uri.toURL());
            if (bufferedImage == null) {
                throw new IllegalArgumentException("Could not decode image from URI: " + uri);
            }
            return bufferedImage;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read image from URI: " + uri, e);
        }
    }

    // ======================== RGB Conversion ========================

    /**
     * Converts a BufferedImage to TYPE_INT_RGB, stripping any alpha channel.
     * This handles RGBA PNGs, grayscale images, indexed color, etc.
     */
    static BufferedImage convertToRgb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_RGB) {
            return source;
        }
        BufferedImage rgbImage = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgbImage.createGraphics();
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        return rgbImage;
    }
}
