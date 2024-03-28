package dev.langchain4j.model.output;

import java.util.HashSet;
import java.util.Set;

import static java.util.Arrays.asList;

public class SetOutputParser implements OutputParser<Set<String>> {

    @Override
    public Set<String> parse(String string) {
        return new HashSet<>(asList(string.split("\n")));
    }

    @Override
    public String formatInstructions() {
        return "\nYou must put every item on a separate line.";
    }
}
