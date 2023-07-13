package dev.langchain4j.data.document;

import dev.langchain4j.data.segment.TextSegment;

import java.util.Objects;

public class Document {

    private final String text;
    private final Metadata metadata;

    public Document(String text, Metadata metadata) {
        this.text = text;
        this.metadata = metadata;
    }

    public String text() {
        return text;
    }

    public Metadata metadata() {
        return metadata;
    }

    public TextSegment toTextSegment() {
        return TextSegment.from(text, metadata);
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
                " text = \"" + text + "\"" +
                " metadata = \"" + metadata + "\"" +
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
