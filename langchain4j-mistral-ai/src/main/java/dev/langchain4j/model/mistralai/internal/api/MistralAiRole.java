package dev.langchain4j.model.mistralai.internal.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum MistralAiRole {

    @JsonProperty("system") SYSTEM,
    @JsonProperty("user") USER,
    @JsonProperty("assistant") ASSISTANT,
    @JsonProperty("tool") TOOL;

    MistralAiRole() {
    }
}
