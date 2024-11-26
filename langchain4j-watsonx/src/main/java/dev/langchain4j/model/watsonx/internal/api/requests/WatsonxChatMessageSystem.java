package dev.langchain4j.model.watsonx.internal.api.requests;

public record WatsonxChatMessageSystem(String role, String content) implements WatsonxChatMessage {
}

