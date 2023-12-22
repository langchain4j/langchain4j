package dev.langchain4j.data.document;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Represents metadata of a Document or a TextSegment.
 * The metadata is stored in a key-value map, where both keys and values are strings.
 * For a Document, the metadata could include information such as the source, creation date,
 * owner, or any other relevant details.
 * For a TextSegment, in addition to metadata copied from a document, it can also include segment-specific information,
 * such as the page number, the position of the segment within the document, chapter, etc.
 */
public class Metadata {

    private final Map<String, String> metadata;

    public Metadata() {
        this(new HashMap<>());
    }

    public Metadata(Map<String, String> metadata) {
        this.metadata = ensureNotNull(metadata, "metadata");
    }

    public String get(String key) {
        return metadata.get(key);
    }

    public Metadata add(String key, String value) {
        this.metadata.put(key, value);
        return this;
    }

    /**
     * Use {@link #add(String, String)} instead
     */
    @Deprecated
    public Metadata add(String key, Object value) {
        return add(key, value.toString());
    }

    public Metadata remove(String key) {
        this.metadata.remove(key);
        return this;
    }

    public Metadata copy() {
        return new Metadata(new HashMap<>(metadata));
    }

    public Map<String, String> asMap() {
        return new HashMap<>(metadata);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Metadata that = (Metadata) o;
        return Objects.equals(this.metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(metadata);
    }

    @Override
    public String toString() {
        return "Metadata {" +
                " metadata = " + metadata +
                " }";
    }

    public static Metadata from(String key, String value) {
        return new Metadata().add(key, value);
    }

    /**
     * Use {@link #from(String, String)} instead
     */
    @Deprecated
    public static Metadata from(String key, Object value) {
        return new Metadata().add(key, value);
    }

    public static Metadata from(Map<String, String> metadata) {
        return new Metadata(metadata);
    }

    public static Metadata metadata(String key, String value) {
        return from(key, value);
    }

    /**
     * Use {@link #metadata(String, String)} instead
     */
    @Deprecated
    public static Metadata metadata(String key, Object value) {
        return from(key, value);
    }
}
