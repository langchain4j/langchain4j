package dev.langchain4j.service.output;

import dev.langchain4j.internal.Json;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static java.util.stream.Collectors.toCollection;

class StringSetOutputParser extends CollectionOutputParser<Set<String>> {

    @Override
    public Set<String> parse(String text) {
        if (text == null) {
            throw ParsingUtils.outputParsingException(text, getType(), null);
        }

        if (text.isBlank()) {
            return new LinkedHashSet<>();
        }

        if (text.trim().startsWith("{")) {
            Map<?, ?> map = Json.fromJson(text, Map.class);
            if (map == null || map.isEmpty()) {
                throw ParsingUtils.outputParsingException(text, getType(), null);
            }

            Object items;
            if (map.containsKey("items")) {
                items = map.get("items");
            } else {
                items = map.values().iterator().next();
            }

            if (items == null) {
                throw ParsingUtils.outputParsingException(text, getType(), null);
            } else if (items instanceof String) {
                items = List.of(items);
            }

            return ((Collection<String>) items).stream()
                    .map(String::trim)
                    .filter(line -> !isNullOrBlank(line))
                    .collect(toCollection(LinkedHashSet::new));
        }

        return Stream.of(text.split("\n"))
                .map(String::trim)
                .filter(line -> !isNullOrBlank(line))
                .collect(toCollection(LinkedHashSet::new));
    }

    private String getType() {
        return "java.util.Set<java.lang.String>";
    }
}
