package dev.langchain4j.model.mistralai.internal.api;

import static dev.langchain4j.model.chat.request.ResponseFormat.JSON;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.model.chat.request.ResponseFormat;

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
    @JsonProperty("text")
    TEXT,
    @JsonProperty("json_object")
    JSON_OBJECT;

    MistralAiResponseFormatType() {}

    public ResponseFormat toGenericResponseFormat() {
        return switch (this) {
            case TEXT -> ResponseFormat.TEXT;
            case JSON_OBJECT -> JSON;
        };
    }

    /**
     * Returns the string representation in lowercase of the response format type.
     */
    @Override
    public String toString() {
        return this.name().toLowerCase();
    }
}
