package dev.langchain4j.model.watsonx.internal.api.requests;

public record WatsonxChatMessageTool(String role, String content, String toolCallId) implements WatsonxChatMessage {

}
