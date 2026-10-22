package dev.langchain4j.service.output;

import static dev.langchain4j.service.output.ParsingUtils.parseAsStringOrJson;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME;

import dev.langchain4j.Internal;
import java.time.LocalDateTime;

@Internal
class LocalDateTimeOutputParser implements OutputParser<LocalDateTime> {

    @Override
    public LocalDateTime parse(String text) {
        return parseAsStringOrJson(text, LocalDateTimeOutputParser::parseLocalDateTime, LocalDateTime.class);
    }

    private static LocalDateTime parseLocalDateTime(String text) {
        return LocalDateTime.parse(text.trim(), ISO_LOCAL_DATE_TIME);
    }

    @Override
    public String formatInstructions() {
        return "yyyy-MM-ddTHH:mm:ss";
    }
}
