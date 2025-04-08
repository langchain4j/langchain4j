package dev.langchain4j.service.output;

import static dev.langchain4j.service.output.ParsingUtils.outputParsingException;
import static dev.langchain4j.service.output.ParsingUtils.parseAsValueOrJson;

class BooleanOutputParser implements OutputParser<Boolean> {

    @Override
    public Boolean parse(String text) {
        return parseAsValueOrJson(text, BooleanOutputParser::parseBoolean, Boolean.class);
    }

    private static boolean parseBoolean(String text) {
        String trimmed = text.trim();
        if (trimmed.equalsIgnoreCase("true") || trimmed.equalsIgnoreCase("false")) {
            return Boolean.parseBoolean(trimmed);
        } else {
            throw outputParsingException(text, Boolean.class);
        }
    }

    @Override
    public String formatInstructions() {
        return "one of [true, false]";
    }
}
