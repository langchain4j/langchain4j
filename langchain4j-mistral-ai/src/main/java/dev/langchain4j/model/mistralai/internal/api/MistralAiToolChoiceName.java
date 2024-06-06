package dev.langchain4j.model.mistralai.internal.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum MistralAiToolChoiceName {

    @JsonProperty("auto") AUTO,
    @JsonProperty("any") ANY,
    @JsonProperty("none") NONE;

    MistralAiToolChoiceName() {
    }
}
