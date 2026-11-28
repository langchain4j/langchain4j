package dev.langchain4j.guardrail;

import dev.langchain4j.exception.ToolArgumentsException;
import dev.langchain4j.internal.ToolArgumentsParser;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses the arguments of a tool call for the tool guardrail requests, so that the input and the output requests read
 * them the same way.
 */
final class ToolGuardrailArguments {

    private ToolGuardrailArguments() {}

    /**
     * Parses the arguments into a deep, unmodifiable map, leniently, like the default executor of local {@code @Tool}
     * methods.
     *
     * @throws ToolArgumentsException if the arguments are not a valid JSON object
     */
    static Map<String, Object> asMap(String arguments) {
        try {
            return freezeMap(ToolArgumentsParser.argumentsAsMap(arguments));
        } catch (RuntimeException e) {
            throw new ToolArgumentsException(
                    "Tool arguments are not a valid JSON object: %s".formatted(e.getMessage()), e);
        }
    }

    private static Map<String, Object> freezeMap(Map<?, ?> map) {
        // JSON null values are kept, which the map collectors do not allow
        Map<String, Object> copy = new LinkedHashMap<>();
        map.forEach((key, value) -> copy.put(String.valueOf(key), freeze(value)));
        return Collections.unmodifiableMap(copy);
    }

    private static Object freeze(Object value) {
        Object frozen = value;

        if (value instanceof Map<?, ?> map) {
            frozen = freezeMap(map);
        } else if (value instanceof List<?> list) {
            frozen = list.stream().map(ToolGuardrailArguments::freeze).toList();
        }

        return frozen;
    }
}
