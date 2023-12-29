package dev.langchain4j.data.message;

import dev.langchain4j.data.image.Image;

import java.net.URI;
import java.util.Objects;

import static dev.langchain4j.data.message.ContentType.IMAGE;
import static dev.langchain4j.data.message.ImageContent.Granularity.LOW;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

public class ImageContent implements Content {

    private final Image image;
    private final Granularity granularity;

    public ImageContent(URI url) {
        this(url, LOW);
    }

    public ImageContent(URI url, Granularity granularity) {
        this(Image.builder()
                .url(ensureNotNull(url, "url"))
                .build(), granularity);
    }

    public ImageContent(String url) {
        this(URI.create(url));
    }

    public ImageContent(String url, Granularity granularity) {
        this(URI.create(url), granularity);
    }

    public ImageContent(String base64Data, String mimeType) {
        this(base64Data, mimeType, LOW);
    }

    public ImageContent(String base64Data, String mimeType, Granularity granularity) {
        this(Image.builder()
                .base64Data(ensureNotBlank(base64Data, "base64Data"))
                .mimeType(ensureNotBlank(mimeType, "mimeType"))
                .build(), granularity);
    }

    public ImageContent(Image image) {
        this(image, LOW);
    }

    public ImageContent(Image image, Granularity granularity) {
        this.image = ensureNotNull(image, "image");
        this.granularity = ensureNotNull(granularity, "granularity");
    }

    public Image image() {
        return image;
    }

    public Granularity granularity() {
        return granularity;
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
                && Objects.equals(this.granularity, that.granularity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(image, granularity);
    }

    @Override
    public String toString() {
        return "ImageContent {" +
                " image = " + image +
                " granularity = " + granularity +
                " }";
    }

    public static ImageContent from(URI url) {
        return new ImageContent(url);
    }

    public static ImageContent from(URI url, Granularity granularity) {
        return new ImageContent(url, granularity);
    }

    public static ImageContent from(String url) {
        return new ImageContent(url);
    }

    public static ImageContent from(String url, Granularity granularity) {
        return new ImageContent(url, granularity);
    }

    public static ImageContent from(String base64Data, String mimeType) {
        return new ImageContent(base64Data, mimeType);
    }

    public static ImageContent from(String base64Data, String mimeType, Granularity granularity) {
        return new ImageContent(base64Data, mimeType, granularity);
    }

    public static ImageContent from(Image image) {
        return new ImageContent(image);
    }

    public static ImageContent from(Image image, Granularity granularity) {
        return new ImageContent(image, granularity);
    }

    public enum Granularity {

        LOW,
        HIGH,
        AUTO
    }
}
