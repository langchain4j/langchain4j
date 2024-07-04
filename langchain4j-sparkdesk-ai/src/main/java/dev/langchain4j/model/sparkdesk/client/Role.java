package dev.langchain4j.model.sparkdesk.client;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum Role {
    SYSTEM,
    USER,
    ASSISTANT;

    @JsonValue
    public String serialize() {
        return name().toLowerCase(Locale.ROOT);
    }
}