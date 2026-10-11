package dev.langchain4j.model.decision.request;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.message.Content;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Validates the input of a request and copies it into an immutable tree of maps, lists, strings, numbers, booleans
 * and {@code null}s, where the values of the top-level map can also be {@link Content}s or lists of them, or into an
 * immutable list of {@link Content}s.
 */
final class FreeFormValue {

    private FreeFormValue() {}

    static Object ensureValid(Object value, String name) {
        ensureNotNull(value, name);
        if (value instanceof String text) {
            return ensureNotBlank(text, name);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            ensureNotEmpty(map, name)
                    .forEach((key, item) -> result.put(String.valueOf(key), copyTopLevelValue(item, name)));
            return Collections.unmodifiableMap(result);
        }
        if (value instanceof List<?> list) {
            ensureNotEmpty(list, name);
            for (Object content : list) {
                if (!(content instanceof Content)) {
                    throw new IllegalArgumentException(name + " can only contain Contents, but contains "
                            + (content == null ? "null" : content.getClass().getName()));
                }
            }
            return List.copyOf(list);
        }
        throw new IllegalArgumentException(
                name + " must be a String, a Map or a List of Contents, but was " + value.getClass().getName());
    }

    /**
     * A value of the map: like any other value, or a {@link Content} or a non-empty list of {@link Content}s,
     * for example an image.
     */
    private static Object copyTopLevelValue(Object value, String name) {
        if (value instanceof Content) {
            return value;
        }
        if (value instanceof Collection<?> collection
                && !collection.isEmpty()
                && collection.stream().allMatch(Content.class::isInstance)) {
            return List.copyOf(collection);
        }
        return copy(value, name);
    }

    private static Object copy(Object value, String name) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), copy(item, name)));
            return Collections.unmodifiableMap(result);
        }
        if (value instanceof Collection<?> collection) {
            List<Object> result = new ArrayList<>();
            collection.forEach(item -> result.add(copy(item, name)));
            return Collections.unmodifiableList(result);
        }
        throw new IllegalArgumentException(name + " can only contain strings, numbers, booleans, nulls, maps and lists, "
                + "and, as values of the map, Contents or lists of Contents, but contains " + value.getClass().getName());
    }
}
