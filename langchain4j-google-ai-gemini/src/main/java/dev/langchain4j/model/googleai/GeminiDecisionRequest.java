package dev.langchain4j.model.googleai;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import org.jspecify.annotations.Nullable;

@JsonInclude(NON_NULL)
class GeminiDecisionRequest {

    @JsonProperty("model")
    @Nullable String model;

    @JsonProperty("input")
    @Nullable Object input;

    @JsonProperty("questions")
    @Nullable Map<String, Question> questions;

    @JsonIgnore
    @Nullable Map<String, Object> customParameters;

    @JsonAnyGetter
    @Nullable Map<String, Object> customParameters() {
        return customParameters;
    }

    @JsonInclude(NON_NULL)
    record Question(
            @JsonProperty("type") String type,
            @JsonProperty("instructions") String instructions,
            @JsonProperty("criteria") @Nullable Object criteria) {}

    @JsonInclude(NON_NULL)
    record Block(
            @JsonProperty("type") String type,
            @JsonProperty("text") @Nullable String text,
            @JsonProperty("data") @Nullable String data,
            @JsonProperty("uri") @Nullable String uri,
            @JsonProperty("mime_type") @Nullable String mimeType,
            @JsonProperty("resolution") @Nullable String resolution) {

        static Block text(String text) {
            return new Block("text", text, null, null, null, null);
        }
    }
}
