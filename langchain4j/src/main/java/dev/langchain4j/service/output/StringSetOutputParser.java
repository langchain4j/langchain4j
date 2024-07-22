package dev.langchain4j.service.output;

import java.util.LinkedHashSet;
import java.util.Set;

import static java.util.Arrays.asList;

class StringSetOutputParser extends CollectionOutputParser<Set<String>> {

    @Override
    public Set<String> parse(String text) {
        return new LinkedHashSet<>(asList(text.split("\n")));
    }
}
