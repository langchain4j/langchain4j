package dev.langchain4j.model.output;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;

import static dev.langchain4j.internal.Utils.setOf;

public class DateOutputParser implements TextOutputParser<Date> {
    private static final String DATE_PATTERN = "yyyy-MM-dd";
    private static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat(DATE_PATTERN);

    @Override
    public Set<Class<?>> getSupportedTypes() {
        return setOf(Date.class);
    }

    @Override
    public Date parse(String string) {
        string = string.trim();

        // SimpleDateFormat silently accepts dd-MM-yyyy; but parses it strangely.
        if (string.indexOf("-") != 4 || string.indexOf("-", 5) != 7) {
            throw new RuntimeException("Invalid date format: " + string);
        }

        try {
            return SIMPLE_DATE_FORMAT.parse(string);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String formatInstructions() {
        return DATE_PATTERN;
    }
}
