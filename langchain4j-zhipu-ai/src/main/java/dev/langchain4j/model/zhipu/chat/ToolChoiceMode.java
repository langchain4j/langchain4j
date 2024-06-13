package dev.langchain4j.model.zhipu.chat;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum ToolChoiceMode {
    NONE,
    AUTO;

    @JsonValue
    public String serialize() {
        return name().toLowerCase(Locale.ROOT);
    }
}