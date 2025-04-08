package dev.langchain4j.service.output;

import java.util.LinkedHashSet;
import java.util.Set;

import static dev.langchain4j.service.output.ParsingUtils.parseCollectionAsValueOrJson;

class StringSetOutputParser extends CollectionOutputParser<Set<String>> {

    @Override
    public Set<String> parse(String text) {
        return (Set<String>) parseCollectionAsValueOrJson(text, s -> s, LinkedHashSet::new, getType());
    }

    private String getType() {
        return "java.util.Set<java.lang.String>";
    }
}
