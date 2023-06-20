package dev.langchain4j.model.output;

import java.time.LocalDateTime;

import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME;

public class LocalDateTimeOutputParser implements OutputParser<LocalDateTime> {

    @Override
    public LocalDateTime parse(String string) {
        return LocalDateTime.parse(string, ISO_LOCAL_DATE_TIME);
    }

    @Override
    public String formatInstructions() {
        return "2023-12-31T23:59:59";
    }
}
