package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonValue;

public enum GeminiResponseModality {
    TEXT("Text"),
    IMAGE("Image");

    private final String value;

    GeminiResponseModality(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
