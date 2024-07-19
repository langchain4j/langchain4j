package dev.langchain4j.model.output;

import java.util.LinkedHashSet;
import java.util.Set;

import static java.util.Arrays.asList;

public class StringSetOutputParser extends CollectionOutputParser<Set<String>> {

    @Override
    public Set<String> parse(String text) {
        return new LinkedHashSet<>(asList(text.split("\n")));
    }

}
