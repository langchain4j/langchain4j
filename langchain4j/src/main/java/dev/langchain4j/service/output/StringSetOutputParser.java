package dev.langchain4j.service.output;

import dev.langchain4j.internal.Json;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class StringSetOutputParser extends CollectionOutputParser<Set<String>> {

    @Override
    public Set<String> parse(String text) {
        // TODO review once again, check if exception or empty
        if (text == null || text.isBlank()) {
            return new LinkedHashSet<>();
        }

        // Handle JSON input
        if (text.trim().startsWith("{")) {
            Map<?, ?> map = Json.fromJson(text, Map.class);

            if (map == null || map.isEmpty()) {
                return new LinkedHashSet<>();
            }

            Object items;
            if (map.containsKey("items")) {
                items = map.get("items");
            } else {
                // fallback to first property if "items" doesn't exist
                items = map.values().iterator().next();
            }

            if (items == null) {
                return new LinkedHashSet<>();
            }

            // If 'items' is a single string, wrap it into a list
            if (items instanceof String) {
                items = List.of(items);
            }

            // Convert all items to trimmed strings in a LinkedHashSet
            return ((Collection<String>) items).stream()
                    .map(String::trim)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        // Handle plain-text input, splitting by new lines and trimming
        return Stream.of(text.split("\n"))
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
