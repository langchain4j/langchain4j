package dev.langchain4j.data.document;

import dev.langchain4j.data.segment.TextSegment;

import java.util.Objects;

import static dev.langchain4j.internal.Utils.quoted;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Represents an unstructured piece of text that usually corresponds to a content of a single file.
 * This text could originate from various sources such as a text file, PDF, DOCX, or a web page (HTML).
 * Each document may have associated metadata including its source, owner, creation date, etc.
 */
public class Document {

    /**
     * Common metadata key for the name of the file from which the document was loaded.
     */
    public static final String FILE_NAME = "file_name";
    /**
     * Common metadata key for the absolute path of the directory from which the document was loaded.
     */
    public static final String ABSOLUTE_DIRECTORY_PATH = "absolute_directory_path";
    /**
     * Common metadata key for the URL from which the document was loaded.
     */
    public static final String URL = "url";

    private final String text;
    private final Metadata metadata;

    /**
     * Creates a new Document from the given text.
     *
     * <p>The created document will have empty metadata.
     *
     * @param text the text of the document.
     */
    public Document(String text) {
        this(text, new Metadata());
    }

    /**
     * Creates a new Document from the given text.
     *
     * @param text     the text of the document.
     * @param metadata the metadata of the document.
     */
    public Document(String text, Metadata metadata) {
        this.text = ensureNotBlank(text, "text");
        this.metadata = ensureNotNull(metadata, "metadata");
    }

    /**
     * Returns the text of this document.
     *
     * @return the text.
     */
    public String text() {
        return text;
    }

    /**
     * Returns the metadata associated with this document.
     *
     * @return the metadata.
     */
    public Metadata metadata() {
        return metadata;
    }

    /**
     * Looks up the metadata value for the given key.
     *
     * @param key the key to look up.
     * @return the metadata value for the given key, or null if the key is not present.
     * @deprecated as of 0.31.0, use {@link #metadata()} and then {@link Metadata#getString(String)},
     * {@link Metadata#getInteger(String)}, {@link Metadata#getLong(String)}, {@link Metadata#getFloat(String)},
     * {@link Metadata#getDouble(String)} instead.
     */
    @Deprecated
    public String metadata(String key) {
        return metadata.get(key);
    }

    /**
     * Builds a TextSegment from this document.
     *
     * @return a TextSegment.
     */
    public TextSegment toTextSegment() {
        return TextSegment.from(text, metadata.copy().put("index", "0"));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Document that = (Document) o;
        return Objects.equals(this.text, that.text)
                && Objects.equals(this.metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, metadata);
    }

    @Override
    public String toString() {
        return "Document {" +
                " text = " + quoted(text) +
                " metadata = " + metadata.asMap() +
                " }";
    }

    /**
     * Creates a new Document from the given text.
     *
     * <p>The created document will have empty metadata.</p>
     *
     * @param text the text of the document.
     * @return a new Document.
     */
    public static Document from(String text) {
        return new Document(text);
    }

    /**
     * Creates a new Document from the given text.
     *
     * @param text     the text of the document.
     * @param metadata the metadata of the document.
     * @return a new Document.
     */
    public static Document from(String text, Metadata metadata) {
        return new Document(text, metadata);
    }

    /**
     * Creates a new Document from the given text.
     *
     * <p>The created document will have empty metadata.</p>
     *
     * @param text the text of the document.
     * @return a new Document.
     */
    public static Document document(String text) {
        return from(text);
    }

    /**
     * Creates a new Document from the given text.
     *
     * @param text     the text of the document.
     * @param metadata the metadata of the document.
     * @return a new Document.
     */
    public static Document document(String text, Metadata metadata) {
        return from(text, metadata);
    }
}
