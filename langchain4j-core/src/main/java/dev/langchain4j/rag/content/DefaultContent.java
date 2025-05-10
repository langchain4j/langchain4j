package dev.langchain4j.rag.content;

import dev.langchain4j.data.segment.TextSegment;

import java.util.Map;
import java.util.Objects;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * A default implementation of a {@link Content}.
 * <br>
 * The class includes optional metadata which can store additional information about the content.
 * This metadata is supplementary and is intentionally excluded from equality and hash calculations.
 * See {@link #equals(Object)} and {@link #hashCode()} for details.
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
     * The {@code metadata} field is intentionally excluded from the equality check. Metadata is considered
     * supplementary information and does not contribute to the core identity of the {@code Content}.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Content that = (Content) o;
        return Objects.equals(this.textSegment, that.textSegment());
    }

    /**
     * Computes the hash code for this {@code Content}.
     * <br>
     * The {@code metadata} field is excluded from the hash code calculation. This ensures that two logically identical
     * {@code Content} objects with differing metadata produce the same hash code, maintaining consistent behavior in
     * hash-based collections.
     */
    @Override
    public int hashCode() {
        return Objects.hash(textSegment);
    }

    @Override
    public String toString() {
        return "DefaultContent {" +
                " textSegment = " + textSegment +
                ", metadata = " + metadata +
                " }";
    }
}
