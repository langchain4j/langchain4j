package dev.langchain4j.model.ollama;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum OllamaCapability {
    COMPLETION,
    TOOLS,
    INSERT,
    VISION,
    EMBEDDING,
    THINKING;

    @JsonCreator
    public static OllamaCapability fromString(String key) {
        return key == null ? null : OllamaCapability.valueOf(key.toUpperCase());
    }
}
