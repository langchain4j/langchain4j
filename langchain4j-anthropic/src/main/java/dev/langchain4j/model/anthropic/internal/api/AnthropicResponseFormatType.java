package dev.langchain4j.model.anthropic.internal.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum AnthropicResponseFormatType {
    @JsonProperty("json_schema")
    JSON_SCHEMA,
}
