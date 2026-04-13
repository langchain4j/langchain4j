package dev.langchain4j.service.output;

import dev.langchain4j.Internal;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

@Internal
class DateOutputParser implements OutputParser<Date> {

    private static final String DATE_PATTERN = "yyyy-MM-dd";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    @Override
    public Date parse(String string) {
        LocalDate localDate = LocalDate.parse(string.trim(), FORMATTER);
        return Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    @Override
    public String formatInstructions() {
        return DATE_PATTERN;
    }
}
