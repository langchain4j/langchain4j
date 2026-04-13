package dev.langchain4j.service.output;

import dev.langchain4j.Internal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;

@Internal
class DateOutputParser implements OutputParser<Date> {

    private static final String DATE_PATTERN = "yyyy-MM-dd";
    // DateTimeFormatter is immutable and thread-safe, unlike SimpleDateFormat.
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    @Override
    public Date parse(String string) {
        string = string.trim();

        // Guard against inputs like "dd-MM-yyyy" so the error message stays consistent
        // with previous behavior (and independent of formatter-specific exceptions).
        if (string.indexOf("-") != 4 || string.indexOf("-", 5) != 7) {
            throw new RuntimeException("Invalid date format: " + string);
        }

        try {
            LocalDate localDate = LocalDate.parse(string, FORMATTER);
            return Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        } catch (DateTimeParseException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String formatInstructions() {
        return DATE_PATTERN;
    }
}
