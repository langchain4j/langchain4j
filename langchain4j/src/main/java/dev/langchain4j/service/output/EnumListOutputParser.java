package dev.langchain4j.service.output;

import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.service.output.ParsingUtils.parseCollectionAsValueOrJson;

@SuppressWarnings("rawtypes")
class EnumListOutputParser extends EnumCollectionOutputParser<Enum> {

    EnumListOutputParser(Class<? extends Enum> enumClass) {
        super(enumClass);
    }

    @Override
    public List<Enum> parse(String text) {
        return (List<Enum>) parseCollectionAsValueOrJson(text, enumOutputParser::parse, ArrayList::new, getType());
    }

    private String getType() { // TODO, check everywhere
        return "java.util.List<" + enumClass.getName() + ">";
    }
}
