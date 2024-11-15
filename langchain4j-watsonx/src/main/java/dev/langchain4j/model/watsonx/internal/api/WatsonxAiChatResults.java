package dev.langchain4j.model.watsonx.internal.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WatsonxAiChatResults(String generatedText, Integer generatedTokenCount, Integer inputTokenCount, String stopReason) {
}
