package dev.langchain4j.model.decision;

import static dev.langchain4j.internal.Json.fromJson;
import static dev.langchain4j.internal.Json.toJson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class DecisionSnapshots {

    private DecisionSnapshots() {}

    static Map<String, Object> map(Map<String, Object> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        input.forEach((key, value) -> result.put(key, value(value)));
        return Collections.unmodifiableMap(result);
    }

    static Object value(Object input) {
        if (input == null || input instanceof String || input instanceof Number || input instanceof Boolean) {
            return input;
        }
        if (input instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, value) -> {
                if (!(key instanceof String name)) {
                    throw new IllegalArgumentException("Snapshot map keys must be strings");
                }
                result.put(name, value(value));
            });
            return Collections.unmodifiableMap(result);
        }
        if (input instanceof List<?> list) {
            List<Object> result = new ArrayList<>();
            list.forEach(item -> result.add(value(item)));
            return Collections.unmodifiableList(result);
        }
        return value(fromJson(toJson(input), Object.class));
    }
}
