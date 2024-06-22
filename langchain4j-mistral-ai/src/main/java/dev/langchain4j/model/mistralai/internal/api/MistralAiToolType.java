package dev.langchain4j.model.mistralai.internal.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum MistralAiToolType {

    @JsonProperty("function") FUNCTION;

    MistralAiToolType() {
    }
}
