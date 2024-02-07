package dev.langchain4j.data.document;

import dev.langchain4j.data.segment.TextSegment;

import java.util.*;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Represents metadata of a {@link Document} or a {@link TextSegment}.
 * The metadata is stored in a key-value map, where both keys and values are strings. TODO
 * For a {@link Document}, the metadata could include information such as the source, creation date,
 * owner, or any other relevant details.
 * For a {@link TextSegment}, in addition to metadata inherited from a document, it can also include segment-specific
 * information, such as the page number, the position of the segment within the document, chapter, etc.
 */
public class Metadata {

    private static final Set<Class<?>> SUPPORTED_VALUE_TYPES = new HashSet<>();

    static {
        SUPPORTED_VALUE_TYPES.add(String.class);
        SUPPORTED_VALUE_TYPES.add(Integer.class);
        SUPPORTED_VALUE_TYPES.add(Double.class);
        SUPPORTED_VALUE_TYPES.add(Boolean.class);
        // TODO more types
    }

    private final Map<String, Object> metadata;

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
    public Metadata(Map<String, ?> metadata) {
        metadata.forEach((key, value) -> {
            // TODO enforce non-null values here?
            if (value != null) { // TODO test
                if (!SUPPORTED_VALUE_TYPES.contains(value.getClass())) {
                    throw illegalArgument("Metadata key '%s' has value (%s) of unsupported type '%s'. Supported types: %s",
                            key, value, value.getClass(), SUPPORTED_VALUE_TYPES
                    );
                }
            }
        });
        this.metadata = new HashMap<>(ensureNotNull(metadata, "metadata"));
    }

    /**
     * This method is deprecated. Use {@link #getString(String)}, {@link #getInteger(String)}, etc instead.
     * <br>
     * Returns the value associated with the given key.
     *
     * @param key the key
     * @return the value associated with the given key, or null if the key is not present.
     */
    @Deprecated
    public String get(String key) {
        Object value = metadata.get(key);
        if (value != null) {
            return value.toString();
        } else {
            return null;
        }
    }

    /**
     * TODO
     *
     * @param key
     * @return
     */
    public String getString(String key) {
        return (String) metadata.get(key);
    }

    /**
     * TODO
     *
     * @param key
     * @return
     */
    public Integer getInteger(String key) {
        return (Integer) metadata.get(key);
    }

    /**
     * TODO
     *
     * @param key
     * @return
     */
    public Double getDouble(String key) {
        return (Double) metadata.get(key);
    }

    /**
     * TODO
     *
     * @param key
     * @return
     */
    public Boolean getBoolean(String key) {
        return (Boolean) metadata.get(key);
    }

    // TODO other types

    /**
     * TODO
     *
     * @param key
     * @return
     */
    public Object getObject(String key) {
        return metadata.get(key);
    }

    /**
     * This method is deprecated. Use {@link #add(String, String)}, {@link #add(String, Integer)}, and others instead.
     * <br>
     * Adds a key-value pair to the metadata.
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
     * Adds a key-value pair to the metadata.
     *
     * @param key   the key
     * @param value the value
     * @return {@code this}
     */
    public Metadata add(String key, String value) {
        // TODO ensure not null here? check everywhere
        this.metadata.put(key, value);
        return this;
    }

    /**
     * Adds a key-value pair to the metadata.
     *
     * @param key   the key
     * @param value the value
     * @return {@code this}
     */
    public Metadata add(String key, Integer value) {
        this.metadata.put(key, value);
        return this;
    }

    /**
     * Adds a key-value pair to the metadata.
     *
     * @param key   the key
     * @param value the value
     * @return {@code this}
     */
    public Metadata add(String key, Long value) {
        this.metadata.put(key, value);
        return this;
    }

    /**
     * Adds a key-value pair to the metadata.
     *
     * @param key   the key
     * @param value the value
     * @return {@code this}
     */
    public Metadata add(String key, Double value) {
        this.metadata.put(key, value);
        return this;
    }

    /**
     * Adds a key-value pair to the metadata.
     *
     * @param key   the key
     * @param value the value
     * @return {@code this}
     */
    public Metadata add(String key, Boolean value) {
        this.metadata.put(key, value);
        return this;
    }

    // TODO other types

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
     *
     * @return a copy of this Metadata object.
     */
    public Metadata copy() {
        return new Metadata(metadata);
    }

    /**
     * This method is deprecated. Use {@link #toMap()} instead.
     * <br>
     * Get a copy of the metadata as a map of key-value pairs.
     *
     * @return the metadata as a map of key-value pairs.
     */
    @Deprecated
    public Map<String, String> asMap() {
        Map<String, String> map = new HashMap<>();
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            map.put(entry.getKey(), String.valueOf(entry.getValue()));
        }
        return map;
    }

    /**
     * TODO
     *
     * @return
     */
    public Map<String, Object> toMap() {
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
    @Deprecated // TODO
    public static Metadata from(String key, String value) {
        return new Metadata().add(key, value);
    }

    /**
     * @param key   the key
     * @param value the value
     * @return a Metadata object
     * @deprecated Use {@link #from(String, String)} instead
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
    public static Metadata from(Map<String, ?> metadata) {
        return new Metadata(metadata);
    }

    /**
     * Constructs a Metadata object from a single key-value pair.
     *
     * @param key   the key
     * @param value the value
     * @return a Metadata object
     */
    @Deprecated // TODO
    public static Metadata metadata(String key, String value) {
        return from(key, value);
    }

    /**
     * @param key   the key
     * @param value the value
     * @return a Metadata object
     * @deprecated Use {@link #metadata(String, String)} instead
     */
    @Deprecated
    public static Metadata metadata(String key, Object value) {
        return from(key, value);
    }
}
