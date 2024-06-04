package dev.langchain4j.model.output;

import java.time.LocalDate;
import java.util.Set;

import static dev.langchain4j.internal.Utils.setOf;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE;

public class LocalDateOutputParser implements TextOutputParser<LocalDate> {

    @Override
    public Set<Class<?>> getSupportedTypes() {
        return setOf(LocalDate.class);
    }

    @Override
    public LocalDate parse(String string) {
        return LocalDate.parse(string, ISO_LOCAL_DATE);
    }

    @Override
    public String formatInstructions() {
        return "yyyy-MM-dd";
    }
}
