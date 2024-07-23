package dev.langchain4j.service.output;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;

@SuppressWarnings("rawtypes")
class EnumSetOutputParser extends EnumCollectionOutputParser<Enum> {

    EnumSetOutputParser(Class<? extends Enum> enumClass) {
        super(enumClass);
    }

    @Override
    public Set<Enum> parse(String text) {
        List<String> stringsList = asList(text.split("\n"));
        return stringsList.stream()
                .map(enumOutputParser::parse)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
