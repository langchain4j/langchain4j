package dev.langchain4j.model.mistralai.internal.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the value of the 'type' field in the response_format parameter of the MistralAi Chat completions request.
 * <p>
 * Current values are:
 * <ul>
 *     <li>{@link MistralAiResponseFormatType#TEXT}</li>
 *     <li>{@link MistralAiResponseFormatType#JSON_OBJECT}</li>
 * </ul>
 */
public enum MistralAiResponseFormatType {

    @JsonProperty("text") TEXT,
    @JsonProperty("json_object") JSON_OBJECT;

    MistralAiResponseFormatType() {
    }

    /**
     * Returns the string representation in lowercase of the response format type.
     */
    public String toString() {
        return this.name().toLowerCase();
    }

}
