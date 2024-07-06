package dev.langchain4j.model.output;

import java.time.LocalTime;

import static java.time.format.DateTimeFormatter.ISO_LOCAL_TIME;

public class LocalTimeOutputParser implements OutputParser<LocalTime> {

    @Override
    public LocalTime parse(String string) {
        return LocalTime.parse(string.trim(), ISO_LOCAL_TIME);
    }

    @Override
    public String formatInstructions() {
        return "HH:mm:ss";
    }
}
