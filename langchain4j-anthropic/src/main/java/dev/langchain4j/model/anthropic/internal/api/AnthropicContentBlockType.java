package dev.langchain4j.model.anthropic.internal.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum AnthropicContentBlockType {

    @JsonProperty("text")
    TEXT,

    @JsonProperty("tool_use")
    TOOL_USE,

    @JsonProperty("thinking")
    THINKING // TODO

    // TODO redacted_thinking, ANTHROPIC_MAGIC_STRING_TRIGGER_REDACTED_THINKING_46C9A13E193C177646C7398A98432ECCCE4C1253D5E2D82641AC0E52CC2876CB
}
