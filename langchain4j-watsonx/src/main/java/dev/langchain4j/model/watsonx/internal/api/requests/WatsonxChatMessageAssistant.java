package dev.langchain4j.model.watsonx.internal.api.requests;

import java.util.List;

public record WatsonxChatMessageAssistant(String role, String content, List<WatsonxTextChatToolCall> toolCalls) implements WatsonxChatMessage {
}

