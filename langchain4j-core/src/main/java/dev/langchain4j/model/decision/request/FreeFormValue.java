package dev.langchain4j.model.decision.request;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Validates the input of a request and copies it into an immutable tree of maps, lists, strings, numbers, booleans
 * and {@code null}s.
 */
final class FreeFormValue {

    private FreeFormValue() {}

    static Object ensureValid(Object value, String name) {
        ensureNotNull(value, name);
        if (value instanceof String text) {
            return ensureNotBlank(text, name);
        }
        if (value instanceof Map<?, ?> map) {
            return copy(ensureNotEmpty(map, name), name);
        }
        throw new IllegalArgumentException(
                name + " must be a String or a Map, but was " + value.getClass().getName());
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
                + "but contains " + value.getClass().getName());
    }
}
