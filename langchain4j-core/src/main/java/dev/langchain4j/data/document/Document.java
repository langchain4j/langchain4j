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

    public static final String DOCUMENT_TYPE = "document_type";
    public static final String FILE_NAME = "file_name";
    public static final String ABSOLUTE_DIRECTORY_PATH = "absolute_directory_path";
    public static final String URL = "url";

    private final String text;
    private final Metadata metadata;

    public Document(String text, Metadata metadata) {
        this.text = ensureNotBlank(text, "text");
        this.metadata = ensureNotNull(metadata, "metadata");
    }

    public String text() {
        return text;
    }

    public Metadata metadata() {
        return metadata;
    }

    public String metadata(String key) {
        return metadata.get(key);
    }

    public TextSegment toTextSegment() {
        return TextSegment.from(text, metadata.copy().add("index", 0));
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

    public static Document from(String text) {
        return new Document(text, new Metadata());
    }

    public static Document from(String text, Metadata metadata) {
        return new Document(text, metadata);
    }

    public static Document document(String text) {
        return from(text);
    }

    public static Document document(String text, Metadata metadata) {
        return from(text, metadata);
    }
}
