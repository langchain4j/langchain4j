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

    /**
     * Construct a Metadata object with an empty map of key-value pairs.
     */
    public Metadata() {
        this(new HashMap<>());
    }

    /**
     * Constructs a Metadata object from a map of key-value pairs.
     *
     * @param metadata the map of key-value pairs; must not be null.
     */
    public Metadata(Map<String, String> metadata) {
        this.metadata = new HashMap<>(ensureNotNull(metadata, "metadata"));
    }

    /**
     * Returns the value associated with the given key.
     *
     * @param key the key
     * @return the value associated with the given key, or null if the key is not present.
     */
    public String get(String key) {
        return metadata.get(key);
    }

    /**
     * Adds a key-value pair to the metadata.
     *
     * @param key   the key
     * @param value the value
     * @return {@code this}
     */
    public Metadata add(String key, String value) {
        this.metadata.put(key, value);
        return this;
    }

    /**
     * @deprecated Use {@link #add(String, String)} instead
     *
     * @param key   the key
     * @param value the value
     * @return {@code this}
     */
    @Deprecated
    public Metadata add(String key, Object value) {
        return add(key, value.toString());
    }

    /**
     * Removes the given key from the metadata.
     *
     * @param key the key
     * @return {@code this}
     */
    public Metadata remove(String key) {
        this.metadata.remove(key);
        return this;
    }

    /**
     * Copies the metadata.
     * @return a copy of this Metadata object.
     */
    public Metadata copy() {
        return new Metadata(metadata);
    }

    /**
     * Get a copy of the metadata as a map of key-value pairs.
     * @return the metadata as a map of key-value pairs.
     */
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

    /**
     * Constructs a Metadata object from a single key-value pair.
     *
     * @param key   the key
     * @param value the value
     * @return a Metadata object
     */
    public static Metadata from(String key, String value) {
        return new Metadata().add(key, value);
    }

    /**
     * @deprecated  Use {@link #from(String, String)} instead
     *
     * @param key   the key
     * @param value the value
     * @return a Metadata object
     */
    @Deprecated
    public static Metadata from(String key, Object value) {
        return new Metadata().add(key, value);
    }

    /**
     * Constructs a Metadata object from a map of key-value pairs.
     *
     * @param metadata the map of key-value pairs
     * @return a Metadata object
     */
    public static Metadata from(Map<String, String> metadata) {
        return new Metadata(metadata);
    }

    /**
     * Constructs a Metadata object from a single key-value pair.
     *
     * @param key   the key
     * @param value the value
     * @return a Metadata object
     */
    public static Metadata metadata(String key, String value) {
        return from(key, value);
    }

    /**
     * @deprecated Use {@link #metadata(String, String)} instead
     *
     * @param key   the key
     * @param value the value
     * @return a Metadata object
     */
    @Deprecated
    public static Metadata metadata(String key, Object value) {
        return from(key, value);
    }
}
