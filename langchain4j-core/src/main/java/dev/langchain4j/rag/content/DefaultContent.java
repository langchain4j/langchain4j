package dev.langchain4j.rag.content;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.segment.TextSegment;
import java.util.Map;
import java.util.Objects;

/**
 * A default implementation of a {@link Content}.
 * <br>
 * The class includes optional metadata which can store additional information about the content.
 */
public class DefaultContent implements Content {

    private final TextSegment textSegment;
    private final Map<ContentMetadata, Object> metadata;

    public DefaultContent(TextSegment textSegment, Map<ContentMetadata, Object> metadata) {
        this.textSegment = ensureNotNull(textSegment, "textSegment");
        this.metadata = copy(metadata);
    }

    public DefaultContent(String text) {
        this(TextSegment.from(text));
    }

    public DefaultContent(TextSegment textSegment) {
        this(textSegment, Map.of());
    }

    @Override
    public TextSegment textSegment() {
        return textSegment;
    }

    @Override
    public Map<ContentMetadata, Object> metadata() {
        return metadata;
    }

    /**
     * Compares this {@code Content} with another object for equality.
     * <br>
     * Both the {@code textSegment} and {@code metadata} fields contribute to the value of this content.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Content that = (Content) o;
        return Objects.equals(this.textSegment, that.textSegment()) && Objects.equals(this.metadata, that.metadata());
    }

    /**
     * Computes the hash code for this {@code Content}.
     * <br>
     * Both the {@code textSegment} and {@code metadata} fields contribute to the hash code.
     */
    @Override
    public int hashCode() {
        return Objects.hash(textSegment, metadata);
    }

    @Override
    public String toString() {
        return "DefaultContent {" + " textSegment = " + textSegment + ", metadata = " + metadata + " }";
    }
}
