package dev.langchain4j.service.output;

import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.service.output.ParsingUtils.parseCollectionAsValueOrJson;

class StringListOutputParser extends CollectionOutputParser<List<String>> {

    @Override
    public List<String> parse(String text) {
        return (List<String>) parseCollectionAsValueOrJson(text, s -> s, ArrayList::new, getType());
    }

    private String getType() {
        return "java.util.List<java.lang.String>";
    }
}
