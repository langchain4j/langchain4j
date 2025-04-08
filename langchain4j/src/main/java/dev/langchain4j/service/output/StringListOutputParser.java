package dev.langchain4j.service.output;

import dev.langchain4j.internal.Json;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

class StringListOutputParser extends CollectionOutputParser<List<String>> {

    @Override
    public List<String> parse(String text) {
        // TODO review once again, check if exception or empty
        if (text == null || text.isBlank()) {
            return new ArrayList<>();
        }

        if (text.trim().startsWith("{")) {
            Map<?, ?> map = Json.fromJson(text, Map.class);

            if (map == null || map.isEmpty()) {
                return new ArrayList<>();
            }

            Object items;
            if (map.containsKey("items")) {
                items = map.get("items");
            } else {
                items = map.values().iterator().next();
            }

            if (items == null) {
                return new ArrayList<>();
            } else if (items instanceof String) {
                items = List.of(items);
            }
            return ((Collection<String>) items).stream()
                    .toList();
        }

        return Stream.of(text.split("\n"))
                // Trim each line
                .map(String::trim)
                // filter out any empty lines if needed
                .filter(line -> !line.isBlank())
                .toList();
    }

}
