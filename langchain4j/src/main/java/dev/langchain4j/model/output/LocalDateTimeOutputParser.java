package dev.langchain4j.model.output;

import java.time.LocalDateTime;
import java.util.Set;

import static dev.langchain4j.internal.Utils.setOf;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME;

public class LocalDateTimeOutputParser implements TextOutputParser<LocalDateTime> {

    @Override
    public Set<Class<?>> getSupportedTypes() {
        return setOf(LocalDateTime.class);
    }

    @Override
    public LocalDateTime parse(String string) {
        return LocalDateTime.parse(string, ISO_LOCAL_DATE_TIME);
    }

    @Override
    public String formatInstructions() {
        return "yyyy-MM-ddTHH:mm:ss";
    }
}
