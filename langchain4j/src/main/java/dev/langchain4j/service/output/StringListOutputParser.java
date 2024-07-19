package dev.langchain4j.model.output;

import java.util.List;

import static java.util.Arrays.asList;

public class StringListOutputParser extends CollectionOutputParser<List<String>> {

    @Override
    public List<String> parse(String text) {
        return asList(text.split("\n"));
    }

}