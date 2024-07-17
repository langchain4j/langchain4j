package dev.langchain4j.data.message;

import dev.langchain4j.data.richformat.RichFormat;

import java.net.URI;
import java.util.Objects;

import static dev.langchain4j.data.message.ContentType.RICH_FORMAT;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

public class RichFormatContent implements Content {
    
    private final RichFormat richFormat;

    @Override
    public ContentType type() {
        return RICH_FORMAT;
    }

    /**
     * Create a new {@link RichFormatContent} from the given url.
     *
     * @param url the url of the RichFormat.
     */
    public RichFormatContent(URI url) {
        this.richFormat = RichFormat.builder()
            .url(ensureNotNull(url, "url"))
            .build();
    }

    /**
     * Create a new {@link RichFormatContent} from the given url.
     *
     * @param url the url of the RichFormat.
     */
    public RichFormatContent(String url) {
        this(URI.create(url));
    }

    /**
     * Create a new {@link RichFormatContent} from the given base64 data and mime type.
     *
     * @param base64Data the base64 data of the RichFormat.
     * @param mimeType the mime type of the RichFormat.
     */
    public RichFormatContent(String base64Data, String mimeType) {
        this.richFormat = RichFormat.builder()
            .base64Data(ensureNotBlank(base64Data, "base64data"))
            .mimeType(ensureNotBlank(mimeType, "mimeType")).build();
    }

    /**
     * Create a new {@link RichFormatContent} from the given RichFormat.
     *
     * @param richFormat the RichFormat.
     */
    public RichFormatContent(RichFormat richFormat) {
        this.richFormat = richFormat;
    }

    /**
     * Get the {@code RichFormat}.
     * @return the {@code RichFormat}.
     */
    public RichFormat richFormat() {
        return richFormat;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RichFormatContent that = (RichFormatContent) o;
        return Objects.equals(this.richFormat, that.richFormat);
    }

    @Override
    public int hashCode() {
        return Objects.hash(richFormat);
    }

    @Override
    public String toString() {
        return "RichFormatContent {" +
            " richFormat = " + richFormat +
            " }";
    }

    /**
     * Create a new {@link RichFormatContent} from the given url.
     *
     * @param url the url of the RichFormat.
     * @return the new {@link RichFormatContent}.
     */
    public static RichFormatContent from(URI url) {
        return new RichFormatContent(url);
    }

    /**
     * Create a new {@link RichFormatContent} from the given url.
     *
     * @param url the url of the RichFormat.
     * @return the new {@link RichFormatContent}.
     */
    public static RichFormatContent from(String url) {
        return new RichFormatContent(url);
    }

    /**
     * Create a new {@link RichFormatContent} from the given base64 data and mime type.
     *
     * @param base64Data the base64 data of the RichFormat.
     * @param mimeType the mime type of the RichFormat.
     * @return the new {@link RichFormatContent}.
     */
    public static RichFormatContent from(String base64Data, String mimeType) {
        return new RichFormatContent(base64Data, mimeType);
    }

    /**
     * Create a new {@link RichFormatContent} from the given RichFormat.
     *
     * @param richFormat the RichFormat.
     * @return the new {@link RichFormatContent}.
     */
    public static RichFormatContent from(RichFormat richFormat) {
        return new RichFormatContent(richFormat);
    }
}
