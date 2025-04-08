package dev.langchain4j.service.output;

class BooleanOutputParser implements OutputParser<Boolean> {

    @Override
    public Boolean parse(String text) {
        return ParsingUtils.parseAsValueOrJson(text, BooleanOutputParser::parseBoolean, Boolean.class);
    }

    private static boolean parseBoolean(String text) {
        String trimmed = text.trim();
        if (trimmed.equalsIgnoreCase("true") || trimmed.equalsIgnoreCase("false")) {
            return Boolean.parseBoolean(trimmed);
        } else {
            throw outputParsingException(text);
        }
    }

    private static OutputParsingException outputParsingException(String text) {
        return new OutputParsingException("Failed to parse '%s' into java.lang.Boolean".formatted(text));
    }

    @Override
    public String formatInstructions() {
        return "one of [true, false]";
    }
}
