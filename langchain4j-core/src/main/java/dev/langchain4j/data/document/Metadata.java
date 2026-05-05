package dev.langchain4j.data.document;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.Exceptions.runtime;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Represents metadata of a {@link Document} or a {@link TextSegment}.
 * <br>
 * For a {@link Document}, the metadata could store information such as the source, creation date,
 * owner, or any other relevant details.
 * <br>
 * For a {@link TextSegment}, in addition to metadata inherited from a {@link Document}, it can also include
 * segment-specific information, such as the page number, the position of the segment within the document, chapter, etc.
 * <br>
 * The metadata is stored as a key-value map, where the key is a {@link String} and the value can be one of:
 * {@link String}, {@link UUID}, {@link Integer}, {@link Long}, {@link Float}, {@link Double}.
 * If you require additional types, please <a href="https://github.com/langchain4j/langchain4j/issues/new/choose">open an issue</a>.
 * <br>
 * {@code null} values are not permitted.
 */
public class Metadata {

    private static final Set<Class<?>> SUPPORTED_VALUE_TYPES = new LinkedHashSet<>();

    static {
        SUPPORTED_VALUE_TYPES.add(String.class);

        SUPPORTED_VALUE_TYPES.add(UUID.class);

        SUPPORTED_VALUE_TYPES.add(int.class);
        SUPPORTED_VALUE_TYPES.add(Integer.class);

        SUPPORTED_VALUE_TYPES.add(long.class);
        SUPPORTED_VALUE_TYPES.add(Long.class);

        SUPPORTED_VALUE_TYPES.add(float.class);
        SUPPORTED_VALUE_TYPES.add(Float.class);

        SUPPORTED_VALUE_TYPES.add(double.class);
        SUPPORTED_VALUE_TYPES.add(Double.class);

        SUPPORTED_VALUE_TYPES.add(Collection.class);
    }

    private final Map<String, Object> metadata;

    /**
     * Construct a Metadata object with an empty map of key-value pairs.
     */
    public Metadata() {
        this.metadata = new HashMap<>();
    }

    /**
     * Constructs a Metadata object from a map of key-value pairs.
     *
     * @param metadata the map of key-value pairs; must not be {@code null}. {@code null} values are not permitted.
     *                 Supported value types: {@link String}, {@link Integer}, {@link Long}, {@link Float}, {@link Double}, {@link UUID}, {@link Collection}
     */
    public Metadata(Map<String, ?> metadata) {
        validate(metadata);
        this.metadata = new HashMap<>(metadata);
    }

    private static void validate(Map<String, ?> metadata) {
        ensureNotNull(metadata, "metadata").forEach((key, value) -> {
            validate(key, value);
            if (value instanceof Collection<?>) {
                validateCollection(key, (Collection<?>) value);
            } else {
                validateValue(key, value);
            }
        });
    }

    private static void validate(String key, Object value) {
        ensureNotBlank(key, "The metadata key with the value '" + value + "'");
        ensureNotNull(value, "The metadata value for the key '" + key + "'");
    }

    private static void validateValue(String key, Object value) {
        if (!SUPPORTED_VALUE_TYPES.contains(value.getClass())) {
            throw illegalArgument(
                    "The metadata key '%s' has the value '%s', which is of the unsupported type '%s'. "
                            + "Currently, the supported types are: %s",
                    key, value, value.getClass().getName(), SUPPORTED_VALUE_TYPES
            );
        }
    }

    private static void validateCollection(String key, Collection<?> values) {
        Class<?> collectionType = null;

        for (Object value : values) {
            validateValue(key, value);

            if (collectionType == null) {
                collectionType = value.getClass();
            } else if (!collectionType.equals(value.getClass())) {
                throw illegalArgument(
                        "The metadata key '%s' has a collection value with mixed types. "
                                + "Currently, all values in a collection must be of the same type. "
                                + "Offending value: '%s' of type '%s'. Expected type: '%s'.",
                        key, value, value.getClass().getName(), collectionType.getName()
                );
            }
        }
    }

    private <T> Collection<T> getCollection(String key, Class<T> type) {
        Object value = metadata.get(key);

        if (value == null) {
            return Collections.emptyList();
        }

        if (value instanceof Collection<?> collection) {
            if (collection.isEmpty()) {
                return Collections.emptyList();
            }

            return collection.stream()
                    .map(element -> convertElement(key, element, type))
                    .collect(Collectors.toList());
        }

        throw runtime(
                "Metadata entry with the key '%s' has a value of '%s' and type '%s'. "
                        + "It cannot be returned as a Collection.",
                key, value, value.getClass().getName());
    }

    /**
     * Some {@link EmbeddingStore} implementations store values as Strings or numbers as Long.
     * Because of that, we have to convert the {@link EmbeddingStore}s specific value to the requested format.
     * @param key the key
     * @param element the element to convert
     * @param type the type expected to be received
     * @return the converted value associated with the key
     */
    @SuppressWarnings("unchecked")
    private <T> T convertElement(String key, Object element, Class<T> type) {
        if (type.isInstance(element)) {
            return (T) element;
        }

        if (element instanceof String string) {
            if (type == Integer.class) return (T) Integer.valueOf(Integer.parseInt(string));
            if (type == Long.class)    return (T) Long.valueOf(Long.parseLong(string));
            if (type == Float.class)   return (T) Float.valueOf(Float.parseFloat(string));
            if (type == Double.class)  return (T) Double.valueOf(Double.parseDouble(string));
            if (type == UUID.class)    return (T) UUID.fromString(string);
        }

        if (element instanceof Number number) {
            if (type == Integer.class) return (T) Integer.valueOf(number.intValue());
            if (type == Long.class)    return (T) Long.valueOf(number.longValue());
            if (type == Float.class)   return (T) Float.valueOf(number.floatValue());
            if (type == Double.class)  return (T) Double.valueOf(number.doubleValue());
        }

        throw runtime(
                "Metadata entry with the key '%s' has a collection element '%s' of type '%s'. "
                        + "It cannot be converted to %s.",
                key, element, element.getClass().getName(), type.getSimpleName());
    }

    /**
     * Returns the {@code String} value associated with the given key.
     *
     * @param key the key
     * @return the {@code String} value associated with the given key, or {@code null} if the key is not present.
     * @throws RuntimeException if the value is not of type String
     */
    @Nullable
    public String getString(String key) {
        if (!containsKey(key)) {
            return null;
        }

        Object value = metadata.get(key);
        if (value instanceof String string) {
            return string;
        }

        throw runtime(
                "Metadata entry with the key '%s' has a value of '%s' and type '%s'. "
                        + "It cannot be returned as a String.",
                key, value, value.getClass().getName());
    }

    /**
     * Returns the {@code UUID} value associated with the given key.
     *
     * @param key the key
     * @return the {@code UUID} value associated with the given key, or {@code null} if the key is not present.
     * @throws RuntimeException if the value is not of type String
     */
    @Nullable
    public UUID getUUID(String key) {
        if (!containsKey(key)) {
            return null;
        }

        Object value = metadata.get(key);
        if (value instanceof UUID iD) {
            return iD;
        }
        if (value instanceof String string) {
            return UUID.fromString(string);
        }

        throw runtime(
                "Metadata entry with the key '%s' has a value of '%s' and type '%s'. "
                        + "It cannot be returned as a UUID.",
                key, value, value.getClass().getName());
    }

    /**
     * Returns the {@code Integer} value associated with the given key.
     * <br>
     * Some {@link EmbeddingStore} implementations (still) store {@code Metadata} values as {@code String}s.
     * In this case, the {@code String} value will be parsed into an {@code Integer} when this method is called.
     * <br>
     * Some {@link EmbeddingStore} implementations store {@code Metadata} key-value pairs as JSON.
     * In this case, type information is lost when serializing to JSON and then deserializing back from JSON.
     * JSON libraries can, for example, serialize an {@code Integer} and then deserialize it as a {@code Long}.
     * Or serialize a {@code Float} and then deserialize it as a {@code Double}, and so on.
     * In such cases, the actual value will be cast to an {@code Integer} when this method is called.
     *
     * @param key the key
     * @return the {@link Integer} value associated with the given key, or {@code null} if the key is not present.
     * @throws RuntimeException if the value is not {@link Number}
     */
    @Nullable
    public Integer getInteger(String key) {
        if (!containsKey(key)) {
            return null;
        }

        Object value = metadata.get(key);
        if (value instanceof String) {
            return Integer.parseInt(value.toString());
        } else if (value instanceof Number number) {
            return number.intValue();
        }

        throw runtime(
                "Metadata entry with the key '%s' has a value of '%s' and type '%s'. "
                        + "It cannot be returned as an Integer.",
                key, value, value.getClass().getName());
    }

    /**
     * Returns the {@code Long} value associated with the given key.
     * <br>
     * Some {@link EmbeddingStore} implementations (still) store {@code Metadata} values as {@code String}s.
     * In this case, the {@code String} value will be parsed into an {@code Long} when this method is called.
     * <br>
     * Some {@link EmbeddingStore} implementations store {@code Metadata} key-value pairs as JSON.
     * In this case, type information is lost when serializing to JSON and then deserializing back from JSON.
     * JSON libraries can, for example, serialize an {@code Integer} and then deserialize it as a {@code Long}.
     * Or serialize a {@code Float} and then deserialize it as a {@code Double}, and so on.
     * In such cases, the actual value will be cast to a {@code Long} when this method is called.
     *
     * @param key the key
     * @return the {@code Long} value associated with the given key, or {@code null} if the key is not present.
     * @throws RuntimeException if the value is not {@link Number}
     */
    @Nullable
    public Long getLong(String key) {
        if (!containsKey(key)) {
            return null;
        }

        Object value = metadata.get(key);
        if (value instanceof String) {
            return Long.parseLong(value.toString());
        } else if (value instanceof Number number) {
            return number.longValue();
        }

        throw runtime(
                "Metadata entry with the key '%s' has a value of '%s' and type '%s'. "
                        + "It cannot be returned as a Long.",
                key, value, value.getClass().getName());
    }

    /**
     * Returns the {@code Float} value associated with the given key.
     * <br>
     * Some {@link EmbeddingStore} implementations (still) store {@code Metadata} values as {@code String}s.
     * In this case, the {@code String} value will be parsed into a {@code Float} when this method is called.
     * <br>
     * Some {@link EmbeddingStore} implementations store {@code Metadata} key-value pairs as JSON.
     * In this case, type information is lost when serializing to JSON and then deserializing back from JSON.
     * JSON libraries can, for example, serialize an {@code Integer} and then deserialize it as a {@code Long}.
     * Or serialize a {@code Float} and then deserialize it as a {@code Double}, and so on.
     * In such cases, the actual value will be cast to a {@code Float} when this method is called.
     *
     * @param key the key
     * @return the {@code Float} value associated with the given key, or {@code null} if the key is not present.
     * @throws RuntimeException if the value is not {@link Number}
     */
    @Nullable
    public Float getFloat(String key) {
        if (!containsKey(key)) {
            return null;
        }

        final var value = metadata.get(key);
        if (value instanceof String str) {
            return Float.parseFloat(str);
        } else if (value instanceof Number number) {
            return number.floatValue();
        }

        throw runtime(
                "Metadata entry with the key '%s' has a value of '%s' and type '%s'. "
                        + "It cannot be returned as a Float.",
                key, value, value.getClass().getName());
    }

    /**
     * Returns the {@code Double} value associated with the given key.
     * <br>
     * Some {@link EmbeddingStore} implementations (still) store {@code Metadata} values as {@code String}s.
     * In this case, the {@code String} value will be parsed into a {@code Double} when this method is called.
     * <br>
     * Some {@link EmbeddingStore} implementations store {@code Metadata} key-value pairs as JSON.
     * In this case, type information is lost when serializing to JSON and then deserializing back from JSON.
     * JSON libraries can, for example, serialize an {@code Integer} and then deserialize it as a {@code Long}.
     * Or serialize a {@code Float} and then deserialize it as a {@code Double}, and so on.
     * In such cases, the actual value will be cast to a {@code Double} when this method is called.
     *
     * @param key the key
     * @return the {@code Double} value associated with the given key, or {@code null} if the key is not present.
     * @throws RuntimeException if the value is not {@link Number}
     */
    @Nullable
    public Double getDouble(String key) {
        if (!containsKey(key)) {
            return null;
        }

        Object value = metadata.get(key);
        if (value instanceof String) {
            return Double.parseDouble(value.toString());
        } else if (value instanceof Number number) {
            return number.doubleValue();
        }

        throw runtime(
                "Metadata entry with the key '%s' has a value of '%s' and type '%s'. "
                        + "It cannot be returned as a Double.",
                key, value, value.getClass().getName());
    }

    /**
     * Returns the {@code Collection<String>} associated with the given key.
     *
     * @param key the key
     * @return the {@code Collection<String>} associated with the given key, or an empty collection if the key is not present.
     * @throws RuntimeException if the value is not a collection or cannot be converted to {@code String}
     */
    public Collection<String> getStrings(String key) {
        return getCollection(key, String.class);
    }

    /**
     * Returns the {@code Collection<Integer>} associated with the given key.
     *
     * @param key the key
     * @return the {@code Collection<Integer>} associated with the given key, or an empty collection if the key is not present.
     * @throws RuntimeException if the value is not a collection or cannot be converted to {@code Integer}
     */
    public Collection<Integer> getIntegers(String key) {
        return getCollection(key, Integer.class);
    }

    /**
     * Returns the {@code Collection<Long>} associated with the given key.
     *
     * @param key the key
     * @return the {@code Collection<Long>} associated with the given key, or an empty collection if the key is not present.
     * @throws RuntimeException if the value is not a collection or cannot be converted to {@code Long}
     */
    public Collection<Long> getLongs(String key) {
        return getCollection(key, Long.class);
    }

    /**
     * Returns the {@code Collection<Float>} associated with the given key.
     *
     * @param key the key
     * @return the {@code Collection<Float>} associated with the given key, or an empty collection if the key is not present.
     * @throws RuntimeException if the value is not a collection or cannot be converted to {@code Float}
     */
    public Collection<Float> getFloats(String key) {
        return getCollection(key, Float.class);
    }

    /**
     * Returns the {@code Collection<Double>} associated with the given key.
     *
     * @param key the key
     * @return the {@code Collection<Double>} associated with the given key, or an empty collection if the key is not present.
     * @throws RuntimeException if the value is not a collection or cannot be converted to {@code Double}
     */
    public Collection<Double> getDoubles(String key) {
        return getCollection(key, Double.class);
    }

    /**
     * Returns the {@code Collection<UUID>} associated with the given key.
     *
     * @param key the key
     * @return the {@code Collection<UUID>} associated with the given key, or an empty collection if the key is not present.
     * @throws RuntimeException if the value is not a collection or cannot be converted to {@code UUID}
     */
    public Collection<UUID> getUUIDs(String key) {
        return getCollection(key, UUID.class);
    }

    /**
     * Check whether this {@code Metadata} contains a given key.
     *
     * @param key the key
     * @return {@code true} if this metadata contains a given key; {@code false} otherwise.
     */
    public boolean containsKey(String key) {
        return metadata.containsKey(key);
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
    public Metadata put(String key, UUID value) {
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
    public Metadata put(String key, int value) {
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
     * Adds a key-value pair to the metadata.
     *
     * @param key   the key
     * @param values the values; must not be {@code null} or empty; elements must not be {@code null}
     * @return {@code this}
     */
    public Metadata put(String key, Collection<?> values) {
        validate(key, values);
        validateCollection(key, values);
        this.metadata.put(key, List.copyOf(values));
        return this;
    }

    public Metadata putAll(Map<String, Object> metadata) {
        validate(metadata);
        this.metadata.putAll(metadata);
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
        return "Metadata {" + " metadata = " + metadata + " }";
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
     * Merges the current Metadata object with another Metadata object.
     * The two Metadata objects must not have any common keys.
     *
     * @param another The Metadata object to be merged with the current Metadata object.
     * @return A new Metadata object that contains all key-value pairs from both Metadata objects.
     * @throws IllegalArgumentException if there are common keys between the two Metadata objects.
     */
    public Metadata merge(@Nullable Metadata another) {
        if (another == null || another.metadata.isEmpty()) {
            return this.copy();
        }
        final var thisMap = this.toMap();
        final var anotherMap = another.toMap();
        final var commonKeys = new HashSet<>(thisMap.keySet());
        commonKeys.retainAll(anotherMap.keySet());
        if (!commonKeys.isEmpty()) {
            throw illegalArgument("Metadata keys are not unique. Common keys: %s", commonKeys);
        }
        final var mergedMap = new HashMap<>(thisMap);
        mergedMap.putAll(anotherMap);
        return Metadata.from(mergedMap);
    }
}
