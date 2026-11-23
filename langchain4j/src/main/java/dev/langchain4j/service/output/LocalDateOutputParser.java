package dev.langchain4j.service.output;

import static dev.langchain4j.service.output.ParsingUtils.parseAsStringOrJson;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE;

import dev.langchain4j.Internal;
import java.time.LocalDate;

@Internal
class LocalDateOutputParser implements OutputParser<LocalDate> {

    @Override
    public LocalDate parse(String text) {
        return parseAsStringOrJson(text, LocalDateOutputParser::parseLocalDate, LocalDate.class);
    }

    private static LocalDate parseLocalDate(String text) {
        return LocalDate.parse(text.trim(), ISO_LOCAL_DATE);
    }

    @Override
    public String formatInstructions() {
        return "yyyy-MM-dd";
    }
}
