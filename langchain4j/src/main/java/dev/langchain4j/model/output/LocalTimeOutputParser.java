package dev.langchain4j.model.output;

import java.time.LocalTime;
import java.util.Set;

import static dev.langchain4j.internal.Utils.setOf;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_TIME;

public class LocalTimeOutputParser implements TextOutputParser<LocalTime> {

    @Override
    public Set<Class<?>> getSupportedTypes() {
        return setOf(LocalTime.class);
    }

    @Override
    public LocalTime parse(String string) {
        return LocalTime.parse(string, ISO_LOCAL_TIME);
    }

    @Override
    public String formatInstructions() {
        return "HH:mm:ss";
    }
}
