package dev.langchain4j.data.message;

import dev.langchain4j.data.image.Image;

import java.net.URI;
import java.util.Objects;

import static dev.langchain4j.data.message.ContentType.IMAGE;
import static dev.langchain4j.data.message.ImageContent.DetailLevel.LOW;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

public class ImageContent implements Content {

    private final Image image;
    private final DetailLevel detailLevel;

    public ImageContent(URI url) {
        this(url, LOW);
    }

    public ImageContent(URI url, DetailLevel detailLevel) {
        this(Image.builder()
                .url(ensureNotNull(url, "url"))
                .build(), detailLevel);
    }

    public ImageContent(String url) {
        this(URI.create(url));
    }

    public ImageContent(String url, DetailLevel detailLevel) {
        this(URI.create(url), detailLevel);
    }

    public ImageContent(String base64Data, String mimeType) {
        this(base64Data, mimeType, LOW);
    }

    public ImageContent(String base64Data, String mimeType, DetailLevel detailLevel) {
        this(Image.builder()
                .base64Data(ensureNotBlank(base64Data, "base64Data"))
                .mimeType(ensureNotBlank(mimeType, "mimeType"))
                .build(), detailLevel);
    }

    public ImageContent(Image image) {
        this(image, LOW);
    }

    public ImageContent(Image image, DetailLevel detailLevel) {
        this.image = ensureNotNull(image, "image");
        this.detailLevel = ensureNotNull(detailLevel, "detailLevel");
    }

    public Image image() {
        return image;
    }

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

    public static ImageContent from(URI url) {
        return new ImageContent(url);
    }

    public static ImageContent from(URI url, DetailLevel detailLevel) {
        return new ImageContent(url, detailLevel);
    }

    public static ImageContent from(String url) {
        return new ImageContent(url);
    }

    public static ImageContent from(String url, DetailLevel detailLevel) {
        return new ImageContent(url, detailLevel);
    }

    public static ImageContent from(String base64Data, String mimeType) {
        return new ImageContent(base64Data, mimeType);
    }

    public static ImageContent from(String base64Data, String mimeType, DetailLevel detailLevel) {
        return new ImageContent(base64Data, mimeType, detailLevel);
    }

    public static ImageContent from(Image image) {
        return new ImageContent(image);
    }

    public static ImageContent from(Image image, DetailLevel detailLevel) {
        return new ImageContent(image, detailLevel);
    }

    public enum DetailLevel {

        LOW,
        HIGH,
        AUTO
    }
}
