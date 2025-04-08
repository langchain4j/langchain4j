package dev.langchain4j.service.output;

import dev.langchain4j.internal.Json;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import static dev.langchain4j.internal.Utils.isNullOrBlank;

class ParsingUtils {

    static <T> T parseAsValueOrJson(String text, Function<String, T> parser, Type type) {

        if (isNullOrBlank(text)) {
            throw outputParsingException(text, type);
        }

        if (text.trim().startsWith("{")) {
            Map<?, ?> map = Json.fromJson(text, Map.class);
            if (map == null || map.isEmpty()) {
                throw outputParsingException(text, type);
            }

            Object value;
            if (map.containsKey("value")) {
                value = map.get("value");
            } else {
                value = map.values().iterator().next();  // fallback to first property
            }

            if (value == null) {
                throw outputParsingException(text, type);
            }

            return parse(value.toString(), parser, type);
        }

        return parse(text, parser, type);
    }

    static <T> Collection<T> parseCollectionAsValueOrJson(String text,
                                                          Function<String, T> parser,
                                                          Supplier<Collection<T>> emptyCollectionSupplier,
                                                          String type) {
        if (text == null) {
            throw ParsingUtils.outputParsingException(text, type, null);
        }

        if (text.isBlank()) {
            return emptyCollectionSupplier.get();
        }

        if (text.trim().startsWith("{")) {
            Map<?, ?> map = Json.fromJson(text, Map.class);
            if (map == null || map.isEmpty()) {
                throw ParsingUtils.outputParsingException(text, type, null);
            }

            Object items;
            if (map.containsKey("items")) { // TODO values?
                items = map.get("items");
            } else {
                items = map.values().iterator().next();
            }

            if (items == null) {
                throw ParsingUtils.outputParsingException(text, type, null);
            } else if (items instanceof String) {
                items = List.of(items);
            }

            Collection<T> collection = emptyCollectionSupplier.get();
            for (Object item : ((Collection<?>) items)) {
                String itemAsString;
                if (item instanceof String string) {
                    itemAsString = string;
                } else {
                    itemAsString = Json.toJson(item);
                }
                collection.add(parse(itemAsString, parser, type));
            }
            return collection;
        }

        Collection<T> collection = emptyCollectionSupplier.get();
        for (String line : text.split("\n")) {
            if (isNullOrBlank(line)) {
                continue;
            }
            T item = parse(line.trim(), parser, type);
            collection.add(item);
        }
        return collection;
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
        return new OutputParsingException("Failed to parse '%s' into %s".formatted(text, type), cause);
    }
}
