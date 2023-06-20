package dev.langchain4j.data.document;


import java.util.Objects;

public class DocumentSegment {

    private final String text;
    private final Metadata metadata;

    public DocumentSegment(String text, Metadata metadata) {
        this.text = text;
        this.metadata = metadata;
    }

    public String text() {
        return text;
    }

    public Metadata metadata() {
        return metadata;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DocumentSegment that = (DocumentSegment) o;
        return Objects.equals(this.text, that.text)
                && Objects.equals(this.metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, metadata);
    }

    @Override
    public String toString() {
        return "DocumentSegment {" +
                " text = \"" + text + "\"" +
                " metadata = \"" + metadata + "\"" +
                " }";
    }

    public static DocumentSegment from(String text) {
        return new DocumentSegment(text, new Metadata());
    }

    public static DocumentSegment from(String text, Metadata metadata) {
        return new DocumentSegment(text, metadata);
    }

    public static DocumentSegment documentSegment(String text) {
        return from(text);
    }

    public static DocumentSegment documentSegment(String text, Metadata metadata) {
        return from(text, metadata);
    }
}
