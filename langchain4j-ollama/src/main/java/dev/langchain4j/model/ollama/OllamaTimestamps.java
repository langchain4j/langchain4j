package dev.langchain4j.model.ollama;

import java.time.OffsetDateTime;

class OllamaTimestamps {

    private OllamaTimestamps() {}

    // Ollama returns timestamps with a variable-length fractional-second part (trailing zeros are trimmed).
    // OffsetDateTime.parse uses ISO_OFFSET_DATE_TIME, which parses 0-9 fractional digits natively, so no string
    // manipulation is needed. We drop the sub-second precision, as this level of precision isn't important.
    static OffsetDateTime parse(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime;
        }
        return OffsetDateTime.parse(value.toString()).withNano(0);
    }
}
