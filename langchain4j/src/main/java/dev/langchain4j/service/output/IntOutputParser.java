package dev.langchain4j.service.output;

import static dev.langchain4j.service.output.ParsingUtils.parseAsValueOrJson;
import static dev.langchain4j.service.tool.DefaultToolExecutor.getBoundedLongValue;

class IntOutputParser implements OutputParser<Integer> {

    @Override
    public Integer parse(String text) {
        return parseAsValueOrJson(text, IntOutputParser::parseInt, Integer.class);
    }

    private static Integer parseInt(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException nfe) {
            return (int) getBoundedLongValue(text, "int", Integer.class, Integer.MIN_VALUE, Integer.MAX_VALUE);
        }
    }

    @Override
    public String formatInstructions() {
        return "integer number";
    }
}
