package dev.langchain4j.model.ollama;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

class OllamaDateDeserializer extends JsonDeserializer<OffsetDateTime> {

    // Ollama returns different lengths of the nanosecond part in the date; for some models, it is 8 digits, for others 9.
    // This method removes the nanoseconds, as this level of precision isn't important.
    // input      -> 2024-08-04T00:54:54.764563036+02:00
    // output     -> 2024-08-04T00:54:54.+02:00
    @Override
    public OffsetDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String date = p.getText();
        if (date.contains(".")) {
            String[] parts = date.split("\\.");
            if(parts[1].contains("+")) {
                String nanoseconds = parts[1].substring(0, parts[1].indexOf('+'));
                date = date.replaceAll(nanoseconds, "");
            } else {
                String nanoseconds = parts[1].substring(0, parts[1].indexOf('Z'));
                date = date.replaceAll(nanoseconds, "");
            }
        }
        return OffsetDateTime.parse(date, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
