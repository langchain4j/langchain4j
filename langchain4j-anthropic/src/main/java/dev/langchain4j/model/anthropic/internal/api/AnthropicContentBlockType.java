package dev.langchain4j.model.anthropic.internal.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum AnthropicContentBlockType {

    @JsonProperty("text")
    TEXT,

    @JsonProperty("tool_use")
    TOOL_USE,

    @JsonProperty("thinking")
    THINKING
}
