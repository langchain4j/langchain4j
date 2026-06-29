package dev.langchain4j.model.ollama;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.time.OffsetDateTime;

class OllamaDateDeserializer extends JsonDeserializer<OffsetDateTime> {

    // Ollama returns timestamps with a variable-length fractional-second part (trailing zeros are trimmed).
    // OffsetDateTime.parse uses ISO_OFFSET_DATE_TIME, which parses 0-9 fractional digits natively, so no string
    // manipulation is needed. We drop the sub-second precision, as this level of precision isn't important.
    @Override
    public OffsetDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        return OffsetDateTime.parse(p.getText()).withNano(0);
    }
}
