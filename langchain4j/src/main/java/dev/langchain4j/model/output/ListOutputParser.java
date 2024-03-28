package dev.langchain4j.model.output;

import java.util.List;

import static java.util.Arrays.asList;

public class ListOutputParser implements OutputParser<List<String>>
{

    @Override
    public List<String> parse(String string) {
        return asList(string.split("\n"));
    }

    @Override
    public String formatInstructions() {
        return "\nYou must put every item on a separate line.";
    }
}
