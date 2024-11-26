package dev.langchain4j.model.watsonx.internal.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.langchain4j.model.watsonx.internal.api.requests.WatsonxTextChatToolCall;

import java.util.Date;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WatsonxAiChatCompletionResponse(
    @JsonProperty("model_id") String modelId,
    @JsonProperty("created_at") Date createdAt,
    @JsonProperty("choices") List<TextChatResultChoice> choices,
    @JsonProperty("usage") TextChatUsage usage) {


    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record TextChatResultChoice(Integer index, TextChatResultMessage message, String finishReason) {
    }


    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record TextChatUsage(Integer completionTokens, Integer promptTokens, Integer totalTokens) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record TextChatResultMessage(String role, String content, List<WatsonxTextChatToolCall> toolCalls) {
    }
}
