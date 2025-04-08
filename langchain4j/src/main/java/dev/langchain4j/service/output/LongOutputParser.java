package dev.langchain4j.service.output;

import static dev.langchain4j.service.output.ParsingUtils.parseAsValueOrJson;
import static dev.langchain4j.service.tool.DefaultToolExecutor.getBoundedLongValue;

class LongOutputParser implements OutputParser<Long> {

    @Override
    public Long parse(String text) {
        return parseAsValueOrJson(text, LongOutputParser::parseLong, Long.class);
    }

    private static Long parseLong(String text) {
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException nfe) {
            return getBoundedLongValue(text, "long", Long.class, Long.MIN_VALUE, Long.MAX_VALUE);
        }
    }

    @Override
    public String formatInstructions() {
        return "integer number";
    }
}
