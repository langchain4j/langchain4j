package dev.langchain4j.service.output;

import static dev.langchain4j.service.output.ParsingUtils.parseAsStringOrJson;
import static dev.langchain4j.service.tool.DefaultToolExecutor.getBoundedLongValue;

import dev.langchain4j.Internal;

@Internal
class ShortOutputParser implements OutputParser<Short> {

    @Override
    public Short parse(String text) {
        return parseAsStringOrJson(text, ShortOutputParser::parseShort, Short.class);
    }

    private static Short parseShort(String text) {
        try {
            return Short.parseShort(text);
        } catch (NumberFormatException nfe) {
            return (short) getBoundedLongValue(text, "short", Short.class, Short.MIN_VALUE, Short.MAX_VALUE);
        }
    }

    @Override
    public String formatInstructions() {
        return "integer number in range [-32768, 32767]";
    }
}
