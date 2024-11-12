package dev.langchain4j.service.output;

import dev.langchain4j.internal.Json;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

@SuppressWarnings("rawtypes")
class EnumListOutputParser extends EnumCollectionOutputParser<Enum> {

    EnumListOutputParser(Class<? extends Enum> enumClass) {
        super(enumClass);
    }

    @Override
    public List<Enum> parse(String text) {
        if (text == null || text.isBlank()) {
            return new ArrayList<>();
        }

        if (text.startsWith("{")) {
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
                .map(enumOutputParser::parse)
                .collect(toList());
        }

        return Stream.of(text.split("\n"))
            .map(enumOutputParser::parse)
            .collect(toList());
    }
}
