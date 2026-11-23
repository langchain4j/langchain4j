package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import org.jspecify.annotations.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
record GeminiDecisionResponse(
        @JsonProperty("answers") @Nullable Map<String, Answer> answers,
        @JsonProperty("usage") @Nullable Usage usage,
        @JsonProperty("prompt_feedback") @Nullable PromptFeedback promptFeedback) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Answer(
            @JsonProperty("type") @Nullable String type,
            @JsonProperty("noul") @Nullable Double noul,
            @JsonProperty("choice") @Nullable String choice,
            @JsonProperty("score") @Nullable Double score,
            @JsonProperty("probabilities") @Nullable Map<String, Double> probabilities,
            @JsonProperty("confidence") @Nullable Double confidence) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Usage(
            @JsonProperty("total_input_tokens") @Nullable Integer totalInputTokens,
            @JsonProperty("total_output_tokens") @Nullable Integer totalOutputTokens,
            @JsonProperty("total_tokens") @Nullable Integer totalTokens) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PromptFeedback(
            @JsonProperty("block_reason") @Nullable String blockReason,
            @JsonProperty("block_reason_message") @Nullable String blockReasonMessage) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ErrorResponse(@JsonProperty("error") @Nullable Error error) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Error(
                @JsonProperty("code") @Nullable Object code, @JsonProperty("message") @Nullable String message) {}
    }
}
