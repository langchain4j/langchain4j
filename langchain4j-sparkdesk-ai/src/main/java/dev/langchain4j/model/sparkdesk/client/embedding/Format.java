package dev.langchain4j.model.sparkdesk.client.embedding;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum Format {
    PLAIN,
    JSON,
    XML;

    @JsonValue
    public String serialize() {
        return name().toLowerCase(Locale.ROOT);
    }
}
