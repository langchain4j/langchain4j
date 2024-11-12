package dev.langchain4j.service.output;

import dev.langchain4j.internal.Json;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toCollection;

@SuppressWarnings("rawtypes")
class EnumSetOutputParser extends EnumCollectionOutputParser<Enum> {

    EnumSetOutputParser(Class<? extends Enum> enumClass) {
        super(enumClass);
    }

    @Override
    public Set<Enum> parse(String text) {
        if (text == null || text.isBlank()) {
            return new HashSet<>();
        }

        if (text.startsWith("{")) {
            Map<?, ?> map = Json.fromJson(text, Map.class);

            if (map == null || map.isEmpty()) {
                return new HashSet<>();
            }

            Object items;
            if (map.containsKey("items")) {
                items = map.get("items");
            } else {
                items = map.values().iterator().next();
            }

            if (items == null) {
                return new HashSet<>();
            } else if (items instanceof String) {
                items = Set.of(items);
            }

            return ((Collection<String>) items).stream()
                .map(enumOutputParser::parse)
                .collect(toCollection(LinkedHashSet::new));
        }

        return Stream.of(text.split("\n"))
            .map(enumOutputParser::parse)
            .collect(toCollection(LinkedHashSet::new));
    }
}
