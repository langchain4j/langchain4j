package dev.langchain4j.data.message;

import java.util.Objects;

import static dev.langchain4j.data.message.ContentType.TEXT;
import static dev.langchain4j.internal.Utils.quoted;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

/**
 * Represents a text content.
 */
public class TextContent implements Content {

    private final String text;
    private String cacheType;

    /**
     * Creates a new text content.
     * @param text the text.
     */
    public TextContent(String text) {
        this.text = ensureNotBlank(text, "text");
    }

    /**
     * Creates a new text content with cache.
     * @param text the text.
     * @param cacheType cache type used.
     */
    public TextContent(String text, String cacheType) {
        this.text = ensureNotBlank(text, "text");
        this.cacheType = ensureNotBlank(cacheType, "cacheType");
    }

    /**
     * Returns the text.
     * @return the text.
     */
    public String text() {
        return text;
    }

    /**
     * @return the cacheType used
     */
    public String cacheType() {
        return cacheType;
    }

    @Override
    public ContentType type() {
        return TEXT;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TextContent that = (TextContent) o;
        return Objects.equals(this.text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text);
    }

    @Override
    public String toString() {
        return "TextContent {" +
                " text = " + quoted(text) +
                " }";
    }

    /**
     * Creates a new text content.
     * @param text the text.
     * @return the text content.
     */
    public static TextContent from(String text) {
        return new TextContent(text);
    }

    /**
     * Creates a new text content with cache.
     * @param text the text.
     * @param cacheType cache type used.
     * @return the text content.
     */
    public static TextContent from(String text, String cacheType) {
        return new TextContent(text, cacheType);
    }
}
