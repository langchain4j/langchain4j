package dev.langchain4j.service.output;

import java.util.List;

import static java.util.Arrays.asList;

class StringListOutputParser extends CollectionOutputParser<List<String>> {

    @Override
    public List<String> parse(String text) {
        return asList(text.split("\n"));
    }
}