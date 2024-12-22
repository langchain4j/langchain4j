package dev.langchain4j.rag.content;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.content.injector.ContentInjector;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;

import java.util.Objects;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Represents content relevant to a user {@link Query} with the potential to enhance and ground the LLM's response.
 * <br>
 * Currently, it is limited to text content (i.e., {@link TextSegment}),
 * but future expansions may include support for other modalities (e.g., images, audio, video, etc.).
 * <br>
 * The class includes optional metadata which can store additional information about the content.
 * This metadata is supplementary and is intentionally excluded from equality and hash calculations.
 * See {@link #equals(Object)} and {@link #hashCode()} for details.
 *
 * @see ContentRetriever
 * @see ContentAggregator
 * @see ContentInjector
 */
public class Content {

    private final TextSegment textSegment;

    public Content(String text) {
        this(TextSegment.from(text));
    }

    public Content(TextSegment textSegment) {
        this.textSegment = ensureNotNull(textSegment, "textSegment");
    }

    public TextSegment textSegment() {
        return textSegment;
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
        return Objects.equals(this.textSegment, that.textSegment);
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
        return "Content {" +
                " textSegment = " + textSegment +
                " }";
    }

    public static Content from(String text) {
        return new Content(text);
    }

    public static Content from(TextSegment textSegment) {
        return new Content(textSegment);
    }
}
