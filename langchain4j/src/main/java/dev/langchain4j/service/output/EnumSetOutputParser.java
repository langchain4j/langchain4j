package dev.langchain4j.service.output;

import dev.langchain4j.internal.Json;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
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
        if (text == null) {
            throw ParsingUtils.outputParsingException(text, getType(), null);
        }

        if (text.isBlank()) {
            return new HashSet<>();
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
                    .map(enumOutputParser::parse)
                    .collect(toCollection(LinkedHashSet::new));
        }

        return Stream.of(text.split("\n"))
                .map(enumOutputParser::parse)
                .collect(toCollection(LinkedHashSet::new));
    }

    private String getType() {
        return "java.util.Set<" + enumClass.getName() + ">";
    }
}
