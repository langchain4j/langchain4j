package dev.langchain4j.model.decision.request;

import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.Content;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A {@link DecisionInput} made of named fields, for example a support ticket together with the customer's plan:
 * {@code Map.of("ticket", ticket, "plan", "enterprise")}.
 * <p>
 * The values can be strings, numbers, booleans, {@code null}s, maps and lists. The value of a field can also be a
 * {@link Content} or a list of {@link Content}s, for example a named image
 * ({@code Map.of("comment", comment, "photo", ImageContent.from(...))}); which kinds of contents are supported
 * depends on the decision model, and decision models must send them as contents (never as JSON text) or throw an
 * {@link dev.langchain4j.exception.UnsupportedFeatureException}. Other objects are rejected: convert them to a
 * {@link Map} that holds only what the decision needs.
 *
 * @since 1.23.0
 */
@Experimental
public final class FieldsInput implements DecisionInput {

    private final Map<String, Object> fields;

    private FieldsInput(Map<String, ?> fields) {
        Map<String, Object> copy = new LinkedHashMap<>();
        ensureNotEmpty(fields, "fields").forEach((name, value) -> copy.put(String.valueOf(name), copyField(value)));
        this.fields = Collections.unmodifiableMap(copy);
    }

    /**
     * Creates the input from a copy of the given fields.
     *
     * @throws IllegalArgumentException if {@code fields} is {@code null} or empty, or contains a value that is not
     *                                  supported (see the class description)
     */
    public static FieldsInput from(Map<String, ?> fields) {
        return new FieldsInput(fields);
    }

    /**
     * The fields, keyed by name, in the iteration order of the map they were created from. The map and the maps and
     * lists inside it are unmodifiable.
     */
    public Map<String, Object> fields() {
        return fields;
    }

    @Override
    public DecisionInputType type() {
        return DecisionInputType.FIELDS;
    }

    private static Object copyField(Object value) {
        if (value instanceof Content) {
            return value;
        }
        if (value instanceof Collection<?> collection
                && !collection.isEmpty()
                && collection.stream().allMatch(Content.class::isInstance)) {
            return List.copyOf(collection);
        }
        return copy(value);
    }

    private static Object copy(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), copy(item)));
            return Collections.unmodifiableMap(result);
        }
        if (value instanceof Collection<?> collection) {
            List<Object> result = new ArrayList<>();
            collection.forEach(item -> result.add(copy(item)));
            return Collections.unmodifiableList(result);
        }
        throw new IllegalArgumentException("fields can only contain strings, numbers, booleans, nulls, maps and lists, "
                + "and, as values of a field, Contents or lists of Contents, but contains " + value.getClass().getName());
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof FieldsInput that && fields.equals(that.fields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fields);
    }

    @Override
    public String toString() {
        return "FieldsInput{names=" + fields.keySet() + '}';
    }
}
