package dev.langchain4j.service.output;

import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.Utils.quoted;
import static dev.langchain4j.internal.Utils.toBase64;

import dev.langchain4j.Internal;
import dev.langchain4j.internal.Json;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

@Internal
class ParsingUtils {

    static <T> T parseAsStringOrJson(String text, Function<String, T> parser, Class<T> type) {

        if (isNullOrBlank(text)) {
            throw outputParsingException(text, type);
        }

        if (isJsonObject(text)) {
            Map<?, ?> map = parseJsonObjectOrThrow(text, type.getTypeName());
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

    static <T, CT extends Collection<T>> CT parseAsStringOrJson(
            String text, Function<String, T> parser, Supplier<CT> emptyCollectionSupplier, String type) {
        if (text == null) {
            throw outputParsingException(text, type, null);
        }

        if (isJsonArray(text)) {
            Collection<?> values = parseJsonArrayOrNull(text);
            if (values != null) {
                return parseCollectionValues(values, parser, emptyCollectionSupplier, type);
            }
        } else if (isJsonObject(text)) {
            Map<?, ?> map = parseJsonObjectOrThrow(text, type);
            if (isNullOrEmpty(map)) {
                throw outputParsingException(text, type, null);
            }

            Object values = map.get("values");
            if (!(values instanceof Collection<?>)) {
                throw outputParsingException(text, type, null);
            }

            return parseCollectionValues((Collection<?>) values, parser, emptyCollectionSupplier, type);
        }

        return parseLines(text, parser, emptyCollectionSupplier, type);
    }

    private static Map<?, ?> parseJsonObjectOrThrow(String text, String type) {
        try {
            return Json.fromJson(text, Map.class);
        } catch (RuntimeException e) {
            // unlike a JSON array, text that opens with "{" and does not parse is unusable, so there is no fallback
            throw outputParsingException(text, type, e);
        }
    }

    private static Collection<?> parseJsonArrayOrNull(String text) {
        try {
            return Json.fromJson(text.trim(), Collection.class);
        } catch (RuntimeException e) {
            return null; // the text only looks like a JSON array, e.g. "[apple]\n[banana]"
        }
    }

    private static <T, CT extends Collection<T>> CT parseLines(
            String text, Function<String, T> parser, Supplier<CT> emptyCollectionSupplier, String type) {
        CT collection = emptyCollectionSupplier.get();
        for (String line : text.split("\n")) {
            if (isNullOrBlank(line)) {
                continue;
            }
            collection.add(parse(line.trim(), parser, type));
        }
        return collection;
    }

    private static <T, CT extends Collection<T>> CT parseCollectionValues(
            Collection<?> values, Function<String, T> parser, Supplier<CT> emptyCollectionSupplier, String type) {
        CT collection = emptyCollectionSupplier.get();
        for (Object value : values) {
            String stringValue;
            if (value instanceof String string) {
                stringValue = string;
            } else {
                stringValue = Json.toJson(value);
            }
            collection.add(parse(stringValue, parser, type));
        }
        return collection;
    }

    private static boolean isJsonObject(String text) {
        return text.trim().startsWith("{");
    }

    private static boolean isJsonArray(String text) {
        String trimmed = text.trim();
        return trimmed.startsWith("[") && trimmed.endsWith("]");
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
        return new OutputParsingException(
                "Failed to parse %s (base64: %s) into %s".formatted(quoted(text), quoted(toBase64(text)), type), cause);
    }
}
