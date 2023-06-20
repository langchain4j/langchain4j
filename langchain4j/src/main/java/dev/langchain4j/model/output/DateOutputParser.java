package dev.langchain4j.model.output;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DateOutputParser implements OutputParser<Date> {

    private static final String DATE_PATTERN = "yyyy-MM-dd";
    private static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat(DATE_PATTERN);

    @Override
    public Date parse(String string) {
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
