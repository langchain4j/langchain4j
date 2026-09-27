package dev.langchain4j.model.decision.request;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.internal.Json;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Validates free-form values (state and criteria) and normalizes them into an immutable tree of maps, lists,
 * strings, numbers, booleans and {@code null}s. Other objects are converted with their Java field names, so what the
 * model receives does not depend on how a particular {@code DecisionModel} implementation serializes objects.
 */
final class FreeFormValue {

    private FreeFormValue() {}

    static Object ensureValid(Object value, String name) {
        ensureNotNull(value, name);
        if (value instanceof String text) {
            return ensureNotBlank(text, name);
        }
        if (value instanceof Map<?, ?> map) {
            return normalize(ensureNotEmpty(map, name));
        }
        if (value instanceof List<?> list) {
            return normalize(ensureNotEmpty(list, name));
        }
        throw new IllegalArgumentException(
                name + " must be a String, a Map or a List, but was " + value.getClass().getName());
    }

    private static Object normalize(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Enum<?> constant) {
            return constant.name();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), normalize(item)));
            return Collections.unmodifiableMap(result);
        }
        if (value instanceof Collection<?> collection) {
            List<Object> result = new ArrayList<>();
            collection.forEach(item -> result.add(normalize(item)));
            return Collections.unmodifiableList(result);
        }
        return normalize(Json.fromJson(Json.toJson(value), Object.class));
    }
}
