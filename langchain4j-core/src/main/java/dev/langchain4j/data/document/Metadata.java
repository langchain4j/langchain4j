package dev.langchain4j.data.document;

import dev.langchain4j.data.segment.TextSegment;

import java.util.*;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Represents metadata of a {@link Document} or a {@link TextSegment}.
 * <br>
 * For a {@link Document}, the metadata could store information such as the source, creation date,
 * owner, or any other relevant details.
 * <br>
 * For a {@link TextSegment}, in addition to metadata inherited from a document, it can also include segment-specific
 * information, such as the page number, the position of the segment within the document, chapter, etc.
 * <br>
 * The metadata is stored as a key-value map, where the key is a {@link String} and the value can be one of:
 * {@link String}, {@link Integer}, {@link Long}, {@link Float}, {@link Double}.
 * If you require additional types, please <a href="https://github.com/langchain4j/langchain4j/issues/new/choose">open an issue</a>.
 * <br>
 * {@code null} values are not permitted.
 */
public class Metadata {

    private static final Set<Class<?>> SUPPORTED_VALUE_TYPES = new LinkedHashSet<>();

    static {
        SUPPORTED_VALUE_TYPES.add(String.class);

        SUPPORTED_VALUE_TYPES.add(int.class);
        SUPPORTED_VALUE_TYPES.add(Integer.class);

        SUPPORTED_VALUE_TYPES.add(long.class);
        SUPPORTED_VALUE_TYPES.add(Long.class);

        SUPPORTED_VALUE_TYPES.add(float.class);
        SUPPORTED_VALUE_TYPES.add(Float.class);

        SUPPORTED_VALUE_TYPES.add(double.class);
        SUPPORTED_VALUE_TYPES.add(Double.class);
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
     * @param metadata the map of key-value pairs; must not be {@code null}. {@code null} values are not permitted.
     *                 Supported value types: {@link String}, {@link Integer}, {@link Long}, {@link Float}, {@link Double}
     */
    public Metadata(Map<String, ?> metadata) {
        ensureNotNull(metadata, "metadata").forEach((key, value) -> {
            validate(key, value);
            if (!SUPPORTED_VALUE_TYPES.contains(value.getClass())) {
                throw illegalArgument("The metadata key '%s' has the value '%s', which is of the unsupported type '%s'. " +
                                "Currently, the supported types are: %s",
                        key, value, value.getClass().getName(), SUPPORTED_VALUE_TYPES
                );
            }
        });
        this.metadata = new HashMap<>(metadata);
    }

    private static void validate(String key, Object value) {
        ensureNotBlank(key, "The metadata key with the value '" + value + "'");
        ensureNotNull(value, "The metadata value for the key '" + key + "'");
    }

    /**
     * Returns the value associated with the given key.
     *
     * @param key the key
     * @return the value associated with the given key, or {@code null} if the key is not present.
     * @deprecated Use {@link #getString(String)}, {@link #getInteger(String)}, and other methods instead.
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
     * Returns the {@link String} value associated with the given key.
     *
     * @param key the key
     * @return the {@link String} value associated with the given key, or {@code null} if the key is not present.
     */
    public String getString(String key) {
        return (String) metadata.get(key);
    }

    /**
     * Returns the {@link Integer} value associated with the given key.
     * TODO test and document casting, everywhere
     *
     * @param key the key
     * @return the {@link Integer} value associated with the given key, or {@code null} if the key is not present.
     */
    public Integer getInteger(String key) {
        if (!containsKey(key)) {
            return null;
        }

        Object value = metadata.get(key);
        if (value instanceof String) {
            return Integer.parseInt(value.toString()); // TODO needed? no
        }
        return (int) value;
    }

    /**
     * Returns the {@link Long} value associated with the given key.
     *
     * @param key the key
     * @return the {@link Long} value associated with the given key, or {@code null} if the key is not present.
     */
    public Long getLong(String key) {
        if (!containsKey(key)) {
            return null;
        }

        Object value = metadata.get(key);
        if (value instanceof String) {
            return Long.parseLong(value.toString()); // TODO needed? no
        } else if (value instanceof Integer) { // TODO needed? yes
            return (long) (int) value; // TODO other types?
        }
        return (long) value;
    }

    /**
     * Returns the {@link Float} value associated with the given key.
     *
     * @param key the key
     * @return the {@link Float} value associated with the given key, or {@code null} if the key is not present.
     */
    public Float getFloat(String key) {
        if (!containsKey(key)) {
            return null;
        }

        Object value = metadata.get(key);
        if (value instanceof String) {
            return Float.parseFloat(value.toString()); // TODO needed? no
        } else if (value instanceof Double) {
            Double doubleValue = (Double) value;
            if (doubleValue < -Float.MAX_VALUE || doubleValue > Float.MAX_VALUE) { // TODO test
                // TODO message
                throw illegalArgument("Double value '%s' out of range for a float", doubleValue);
            }
            return doubleValue.floatValue(); // TODO needed? yes
        }
        return (float) value;
    }

    /**
     * Returns the {@link Double} value associated with the given key.
     *
     * @param key the key
     * @return the {@link Double} value associated with the given key, or {@code null} if the key is not present.
     */
    public Double getDouble(String key) {
        if (!containsKey(key)) {
            return null;
        }

        Object value = metadata.get(key);
        if (value instanceof String) { // TODO needed? no
            return Double.parseDouble(value.toString());
        }
        return (double) value;
    }

    /**
     * TODO
     *
     * @param key
     * @return
     */
    public Object getObject(String key) { // TODO REMOVE needed? if yes - better name?
        // TODO check for null?
        return metadata.get(key);
    }

    /**
     * TODO
     *
     * @param key
     * @return
     */
    // TODO test
    public boolean containsKey(String key) {
        return metadata.containsKey(key);
    }

    /**
     * Adds a key-value pair to the metadata.
     *
     * @param key   the key
     * @param value the value
     * @return {@code this}
     * @deprecated Use {@link #put(String, String)}, {@link #put(String, int)}, and other methods instead.
     */
    @Deprecated
    public Metadata add(String key, Object value) {
        return put(key, value.toString());
    }

    /**
     * Adds a key-value pair to the metadata.
     *
     * @param key   the key
     * @param value the value
     * @return {@code this}
     * @deprecated Use {@link #put(String, String)}, {@link #put(String, int)}, and other methods instead.
     */
    @Deprecated
    public Metadata add(String key, String value) {
        validate(key, value);
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
    public Metadata put(String key, String value) {
        validate(key, value);
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
    public Metadata put(String key, int value) { // TODO putInteger?
        validate(key, value);
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
    public Metadata put(String key, long value) {
        validate(key, value);
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
    public Metadata put(String key, float value) {
        validate(key, value);
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
    public Metadata put(String key, double value) {
        validate(key, value);
        this.metadata.put(key, value);
        return this;
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
     *
     * @return a copy of this Metadata object.
     */
    public Metadata copy() {
        return new Metadata(metadata);
    }

    /**
     * Get a copy of the metadata as a map of key-value pairs.
     *
     * @return the metadata as a map of key-value pairs.
     * @deprecated Use {@link #toMap()} instead.
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
     * Get a copy of the metadata as a map of key-value pairs.
     *
     * @return the metadata as a map of key-value pairs.
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
    public static Metadata from(String key, String value) {
        return new Metadata().put(key, value);
    }

    /**
     * Constructs a Metadata object from a single key-value pair.
     *
     * @param key   the key
     * @param value the value
     * @return a Metadata object
     */
    public static Metadata from(String key, int value) {
        return new Metadata().put(key, value);
    }

    /**
     * Constructs a Metadata object from a single key-value pair.
     *
     * @param key   the key
     * @param value the value
     * @return a Metadata object
     */
    public static Metadata from(String key, long value) {
        return new Metadata().put(key, value);
    }

    /**
     * Constructs a Metadata object from a single key-value pair.
     *
     * @param key   the key
     * @param value the value
     * @return a Metadata object
     */
    public static Metadata from(String key, float value) {
        return new Metadata().put(key, value);
    }

    /**
     * Constructs a Metadata object from a single key-value pair.
     *
     * @param key   the key
     * @param value the value
     * @return a Metadata object
     */
    public static Metadata from(String key, double value) {
        return new Metadata().put(key, value);
    }

    /**
     * @param key   the key
     * @param value the value
     * @return a Metadata object
     * @deprecated Use {@link #from(String, String)}, {@link #from(String, int)} and other methods instead.
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
