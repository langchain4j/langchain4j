package dev.langchain4j.service.output;

import java.util.LinkedHashSet;
import java.util.Set;

import static dev.langchain4j.service.output.ParsingUtils.parseCollectionAsValueOrJson;

@SuppressWarnings("rawtypes")
class EnumSetOutputParser extends EnumCollectionOutputParser<Enum> {

    EnumSetOutputParser(Class<? extends Enum> enumClass) {
        super(enumClass);
    }

    @Override
    public Set<Enum> parse(String text) {
        return (Set<Enum>) parseCollectionAsValueOrJson(text, enumOutputParser::parse, LinkedHashSet::new, getType());
    }

    private String getType() { // TODO, check everywhere
        return "java.util.Set<" + enumClass.getName() + ">";
    }
}
