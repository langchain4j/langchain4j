package dev.langchain4j.service.output;

import dev.langchain4j.Internal;
import dev.langchain4j.internal.Json;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.Utils.quoted;

@Internal
class ParsingUtils {

    static <T> T parseAsStringOrJson(String text, Function<String, T> parser, Class<T> type) {

        if (isNullOrBlank(text)) {
            throw outputParsingException(text, type);
        }

        if (isJson(text)) {
            Map<?, ?> map = Json.fromJson(text, Map.class);
            if (isNullOrEmpty(map)) {
                throw outputParsingException(text, type);
            }

            Object value = map.get("value");
            if (value == null) {
                throw outputParsingException(text, type);
            }

            return parse(value.toString(), parser, type);
        } else {
            return parse(text, parser, type);
        }
    }

    static <T, CT extends Collection<T>> CT parseAsStringOrJson(String text,
                                                                Function<String, T> parser,
                                                                Supplier<CT> emptyCollectionSupplier,
                                                                String type) {
        if (text == null) {
            throw ParsingUtils.outputParsingException(text, type, null);
        }

        if (isJson(text)) {
            Map<?, ?> map = Json.fromJson(text, Map.class);
            if (isNullOrEmpty(map)) {
                throw outputParsingException(text, type, null);
            }

            Object values = map.get("values");
            if (!(values instanceof Collection<?>)) {
                throw outputParsingException(text, type, null);
            }

            CT collection = emptyCollectionSupplier.get();
            for (Object value : ((Collection<?>) values)) {
                String stringValue;
                if (value instanceof String string) {
                    stringValue = string;
                } else {
                    stringValue = Json.toJson(value);
                }
                collection.add(parse(stringValue, parser, type));
            }
            return collection;
        } else {
            CT collection = emptyCollectionSupplier.get();
            for (String line : text.split("\n")) {
                if (isNullOrBlank(line)) {
                    continue;
                }
                collection.add(parse(line.trim(), parser, type));
            }
            return collection;
        }
    }

    private static boolean isJson(String text) {
        return text.trim().startsWith("{");
    }

    private static <T> T parse(String text, Function<String, T> parser, Type type) {
        return parse(text, parser, type.getTypeName());
    }

    private static <T> T parse(String text, Function<String, T> parser, String type) {
        try {
            return parser.apply(text);
        } catch (IllegalArgumentException iae) {
            throw outputParsingException(text, type, iae);
        }
    }

    static OutputParsingException outputParsingException(String text, Type type) {
        return outputParsingException(text, type.getTypeName(), null);
    }

    static OutputParsingException outputParsingException(String text, String type, Throwable cause) {
        return new OutputParsingException("Failed to parse %s into %s".formatted(quoted(text), type), cause);
    }
}
