package dev.langchain4j.service.output;

import dev.langchain4j.Internal;

import java.time.LocalDate;

import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE;

@Internal
class LocalDateOutputParser implements OutputParser<LocalDate> {

    @Override
    public LocalDate parse(String string) {
        return LocalDate.parse(string.trim(), ISO_LOCAL_DATE);
    }

    @Override
    public String formatInstructions() {
        return "yyyy-MM-dd";
    }
}
