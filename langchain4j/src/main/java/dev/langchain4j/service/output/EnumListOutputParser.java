package dev.langchain4j.service.output;

import java.util.List;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;

class EnumListOutputParser extends EnumCollectionOutputParser<Enum> {

    public EnumListOutputParser(Class<? extends Enum> enumClass) {
        super(enumClass);
    }

    @Override
    public List<Enum> parse(String text) {
        List<String> stringsList = asList(text.split("\n"));
        return stringsList.stream().map(enumOutputParser::parse).collect(Collectors.toList());
    }
}
