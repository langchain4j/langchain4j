package dev.langchain4j.data.segment;

import dev.langchain4j.data.document.Metadata;

import java.util.Objects;

import static dev.langchain4j.internal.Utils.quoted;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Represents a semantically meaningful segment (chunk/piece/fragment) of a larger entity such as a document or chat conversation.
 * This might be a sentence, a paragraph, or any other discrete unit of text that carries meaning.
 * This class encapsulates a piece of text and its associated metadata.
 */
public class TextSegment {

    private final String text;
    private final Metadata metadata;

    /**
     * Creates a new text segment.
     *
     * @param text     the text.
     * @param metadata the metadata.
     */
    public TextSegment(String text, Metadata metadata) {
        this.text = ensureNotBlank(text, "text");
        this.metadata = ensureNotNull(metadata, "metadata");
    }

    /**
     * Returns the text.
     *
     * @return the text.
     */
    public String text() {
        return text;
    }

    /**
     * Returns the metadata.
     *
     * @return the metadata.
     */
    public Metadata metadata() {
        return metadata;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TextSegment that = (TextSegment) o;
        return Objects.equals(this.text, that.text)
                && Objects.equals(this.metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, metadata);
    }

    @Override
    public String toString() {
        return "TextSegment {" +
                " text = " + quoted(text) +
                " metadata = " + metadata.toMap() +
                " }";
    }

    /**
     * Creates a new text segment.
     *
     * @param text the text.
     * @return the text segment.
     */
    public static TextSegment from(String text) {
        return new TextSegment(text, new Metadata());
    }

    /**
     * Creates a new text segment.
     *
     * @param text     the text.
     * @param metadata the metadata.
     * @return the text segment.
     */
    public static TextSegment from(String text, Metadata metadata) {
        return new TextSegment(text, metadata);
    }

    /**
     * Creates a new text segment.
     *
     * @param text the text.
     * @return the text segment.
     */
    public static TextSegment textSegment(String text) {
        return from(text);
    }

    /**
     * Creates a new text segment.
     *
     * @param text     the text.
     * @param metadata the metadata.
     * @return the text segment.
     */
    public static TextSegment textSegment(String text, Metadata metadata) {
        return from(text, metadata);
    }
}
