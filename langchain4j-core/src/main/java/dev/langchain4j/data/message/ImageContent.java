package dev.langchain4j.data.message;

import dev.langchain4j.data.image.Image;

import java.net.URI;
import java.util.Objects;

import static dev.langchain4j.data.message.ContentType.IMAGE;
import static dev.langchain4j.data.message.ImageContent.DetailLevel.LOW;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Represents an image with a DetailLevel.
 */
public class ImageContent implements Content {
    /**
     * The detail level of an {@code Image}.
     */
    public enum DetailLevel {
        /**
         * Low detail.
         */
        LOW,

        /**
         * High detail.
         */
        HIGH,

        /**
         * Auto detail.
         */
        AUTO
    }

    private final Image image;
    private final DetailLevel detailLevel;

    /**
     * Create a new {@link ImageContent} from the given url.
     *
     * <p>The image will be created with {@code DetailLevel.LOW} detail.</p>
     *
     * @param url the url of the image.
     */
    public ImageContent(URI url) {
        this(url, LOW);
    }

    /**
     * Create a new {@link ImageContent} from the given url.
     *
     * <p>The image will be created with {@code DetailLevel.LOW} detail.</p>
     *
     * @param url the url of the image.
     */
    public ImageContent(String url) {
        this(URI.create(url));
    }

    /**
     * Create a new {@link ImageContent} from the given url and detail level.
     *
     * @param url the url of the image.
     * @param detailLevel the detail level of the image.
     */
    public ImageContent(URI url, DetailLevel detailLevel) {
        this(Image.builder()
                .url(ensureNotNull(url, "url"))
                .build(), detailLevel);
    }

    /**
     * Create a new {@link ImageContent} from the given url and detail level.
     *
     * @param url the url of the image.
     * @param detailLevel the detail level of the image.
     */
    public ImageContent(String url, DetailLevel detailLevel) {
        this(URI.create(url), detailLevel);
    }

    /**
     * Create a new {@link ImageContent} from the given base64 data and mime type.
     *
     * <p>The image will be created with {@code DetailLevel.LOW} detail.</p>
     *
     * @param base64Data the base64 data of the image.
     * @param mimeType the mime type of the image.
     */
    public ImageContent(String base64Data, String mimeType) {
        this(base64Data, mimeType, LOW);
    }

    /**
     * Create a new {@link ImageContent} from the given base64 data and mime type.
     *
     * @param base64Data the base64 data of the image.
     * @param mimeType the mime type of the image.
     * @param detailLevel the detail level of the image.
     */
    public ImageContent(String base64Data, String mimeType, DetailLevel detailLevel) {
        this(Image.builder()
                .base64Data(ensureNotBlank(base64Data, "base64Data"))
                .mimeType(ensureNotBlank(mimeType, "mimeType"))
                .build(), detailLevel);
    }

    /**
     * Create a new {@link ImageContent} from the given image.
     *
     * <p>The image will be created with {@code DetailLevel.LOW} detail.</p>
     *
     * @param image the image.
     */
    public ImageContent(Image image) {
        this(image, LOW);
    }

    /**
     * Create a new {@link ImageContent} from the given image.
     *
     * @param image the image.
     * @param detailLevel the detail level of the image.
     */
    public ImageContent(Image image, DetailLevel detailLevel) {
        this.image = ensureNotNull(image, "image");
        this.detailLevel = ensureNotNull(detailLevel, "detailLevel");
    }

    /**
     * Get the {@code Image}.
     * @return the {@code Image}.
     */
    public Image image() {
        return image;
    }

    /**
     * Get the {@code DetailLevel}.
     * @return the {@code DetailLevel}.
     */
    public DetailLevel detailLevel() {
        return detailLevel;
    }

    @Override
    public ContentType type() {
        return IMAGE;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ImageContent that = (ImageContent) o;
        return Objects.equals(this.image, that.image)
                && Objects.equals(this.detailLevel, that.detailLevel);
    }

    @Override
    public int hashCode() {
        return Objects.hash(image, detailLevel);
    }

    @Override
    public String toString() {
        return "ImageContent {" +
                " image = " + image +
                " detailLevel = " + detailLevel +
                " }";
    }

    /**
     * Create a new {@link ImageContent} from the given url.
     *
     * <p>The image will be created with {@code DetailLevel.LOW} detail.</p>
     *
     * @param url the url of the image.
     * @return the new {@link ImageContent}.
     */
    public static ImageContent from(URI url) {
        return new ImageContent(url);
    }

    /**
     * Create a new {@link ImageContent} from the given url.
     *
     * <p>The image will be created with {@code DetailLevel.LOW} detail.</p>
     *
     * @param url the url of the image.
     * @return the new {@link ImageContent}.
     */
    public static ImageContent from(String url) {
        return new ImageContent(url);
    }

    /**
     * Create a new {@link ImageContent} from the given url and detail level.
     *
     * @param url the url of the image.
     * @param detailLevel the detail level of the image.
     * @return the new {@link ImageContent}.
     */
    public static ImageContent from(URI url, DetailLevel detailLevel) {
        return new ImageContent(url, detailLevel);
    }

    /**
     * Create a new {@link ImageContent} from the given url and detail level.
     *
     * @param url the url of the image.
     * @param detailLevel the detail level of the image.
     * @return the new {@link ImageContent}.
     */
    public static ImageContent from(String url, DetailLevel detailLevel) {
        return new ImageContent(url, detailLevel);
    }

    /**
     * Create a new {@link ImageContent} from the given base64 data and mime type.
     *
     * <p>The image will be created with {@code DetailLevel.LOW} detail.</p>
     *
     * @param base64Data the base64 data of the image.
     * @param mimeType the mime type of the image.
     * @return the new {@link ImageContent}.
     */
    public static ImageContent from(String base64Data, String mimeType) {
        return new ImageContent(base64Data, mimeType);
    }

    /**
     * Create a new {@link ImageContent} from the given base64 data and mime type.
     *
     * @param base64Data the base64 data of the image.
     * @param mimeType the mime type of the image.
     * @param detailLevel the detail level of the image.
     * @return the new {@link ImageContent}.
     */
    public static ImageContent from(String base64Data, String mimeType, DetailLevel detailLevel) {
        return new ImageContent(base64Data, mimeType, detailLevel);
    }

    /**
     * Create a new {@link ImageContent} from the given image.
     *
     * <p>The image will be created with {@code DetailLevel.LOW} detail.</p>
     *
     * @param image the image.
     * @return the new {@link ImageContent}.
     */
    public static ImageContent from(Image image) {
        return new ImageContent(image);
    }

    /**
     * Create a new {@link ImageContent} from the given image.
     *
     * @param image the image.
     * @param detailLevel the detail level of the image.
     * @return the new {@link ImageContent}.
     */
    public static ImageContent from(Image image, DetailLevel detailLevel) {
        return new ImageContent(image, detailLevel);
    }
}
