package dev.langchain4j.data.document;

import dev.langchain4j.data.segment.TextSegment;

import static dev.langchain4j.internal.Utils.quoted;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Represents an unstructured piece of text that usually corresponds to a content of a single file.
 * This text could originate from various sources such as a text file, PDF, DOCX, or a web page (HTML).
 * Each document may have associated {@link Metadata} including its source, owner, creation date, etc.
 */
public interface Document {

    /**
     * Common metadata key for the name of the file from which the document was loaded.
     */
    String FILE_NAME = "file_name";
    /**
     * Common metadata key for the absolute path of the directory from which the document was loaded.
     */
    String ABSOLUTE_DIRECTORY_PATH = "absolute_directory_path";
    /**
     * Common metadata key for the URL from which the document was loaded.
     */
    String URL = "url";

    /**
     * Returns the text of this document.
     *
     * @return the text.
     */
    String text();

    /**
     * Returns the metadata associated with this document.
     *
     * @return the metadata.
     */
    Metadata metadata();

    /**
     * Looks up the metadata value for the given key.
     *
     * @param key the key to look up.
     * @return the metadata value for the given key, or null if the key is not present.
     * @deprecated as of 0.31.0, use {@link #metadata()} and then {@link Metadata#getString(String)},
     * {@link Metadata#getInteger(String)}, {@link Metadata#getLong(String)}, {@link Metadata#getFloat(String)},
     * {@link Metadata#getDouble(String)} instead.
     */
    @Deprecated(forRemoval = true)
    default String metadata(String key) {
        return metadata().get(key);
    }

    /**
     * Builds a {@link TextSegment} from this document.
     *
     * @return a {@link TextSegment}
     */
    default TextSegment toTextSegment() {
        return TextSegment.from(text(), metadata().copy().put("index", "0"));
    }

    /**
     * Creates a new Document from the given text.
     *
     * <p>The created document will have empty metadata.</p>
     *
     * @param text the text of the document.
     * @return a new Document.
     */
    static Document from(String text) {
        return new DefaultDocument(text);
    }

    /**
     * Creates a new Document from the given text.
     *
     * @param text     the text of the document.
     * @param metadata the metadata of the document.
     * @return a new Document.
     */
    static Document from(String text, Metadata metadata) {
        return new DefaultDocument(text, metadata);
    }

    /**
     * Creates a new Document from the given text.
     *
     * <p>The created document will have empty metadata.</p>
     *
     * @param text the text of the document.
     * @return a new Document.
     */
    static Document document(String text) {
        return from(text);
    }

    /**
     * Creates a new Document from the given text.
     *
     * @param text     the text of the document.
     * @param metadata the metadata of the document.
     * @return a new Document.
     */
    static Document document(String text, Metadata metadata) {
        return from(text, metadata);
    }

    /**
     * DefaultDocument is an implementation of the Document interface.
     * It represents an unstructured piece of text with associated metadata.
     */
    record DefaultDocument(
            String text,
            Metadata metadata
    ) implements Document {

        public DefaultDocument(String text, Metadata metadata) {
            this.text = ensureNotBlank(text, "text");
            this.metadata = ensureNotNull(metadata, "metadata");
        }

        @Override
        public String metadata(String key) {
            return metadata.get(key);
        }

        /**
         * Creates a new DefaultDocument from the given text.
         *
         * <p>The created document will have empty metadata.
         *
         * @param text the text of the document.
         */
        public DefaultDocument(String text) {
            this(text, new Metadata());
        }

        @Override
        public String toString() {
            return "DefaultDocument {"
                    + " text = " + quoted(text)  // todo: Be careful with PII
                    + " metadata = " + metadata.toMap()
                    + " }";
        }
    }
}
