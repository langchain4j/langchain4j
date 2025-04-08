package dev.langchain4j.service.output;

import dev.langchain4j.internal.Json;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.function.Function;

import static dev.langchain4j.internal.Utils.isNullOrBlank;

class ParsingUtils {

    static <T> T parseAsValueOrJson(String text, Function<String, T> parser, Type type) {

        if (isNullOrBlank(text)) {
            throw outputParsingException(text, type, null);
        }

        if (text.trim().startsWith("{")) {
            Map<?, ?> map = Json.fromJson(text, Map.class);
            if (map == null || map.isEmpty()) {
                throw outputParsingException(text, type, null);
            }

            Object value;
            if (map.containsKey("value")) {
                value = map.get("value");
            } else {
                value = map.values().iterator().next();  // fallback to first property
            }

            if (value == null) {
                throw outputParsingException(text, type, null);
            }

            return parse(value.toString(), parser, type);
        }

        return parse(text, parser, type);
    }

    private static <T> T parse(String text, Function<String, T> parser, Type type) {
        try {
            return parser.apply(text);
        } catch (IllegalArgumentException iae) {
            throw outputParsingException(text, type, iae);
        }
    }

    static OutputParsingException outputParsingException(String text, Type type, Throwable cause) {
        return outputParsingException(text, type.getTypeName(), cause);
    }

    static OutputParsingException outputParsingException(String text, String type, Throwable cause) {
        return new OutputParsingException("Failed to parse '%s' into %s".formatted(text, type), cause);
    }
}
