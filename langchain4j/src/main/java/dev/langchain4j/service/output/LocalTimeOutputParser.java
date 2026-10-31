package dev.langchain4j.service.output;

import static dev.langchain4j.service.output.ParsingUtils.parseAsStringOrJson;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_TIME;

import dev.langchain4j.Internal;
import java.time.LocalTime;

@Internal
class LocalTimeOutputParser implements OutputParser<LocalTime> {

    @Override
    public LocalTime parse(String text) {
        return parseAsStringOrJson(text, LocalTimeOutputParser::parseLocalTime, LocalTime.class);
    }

    private static LocalTime parseLocalTime(String text) {
        return LocalTime.parse(text.trim(), ISO_LOCAL_TIME);
    }

    @Override
    public String formatInstructions() {
        return "HH:mm:ss";
    }
}
