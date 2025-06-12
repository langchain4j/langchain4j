package dev.langchain4j.service.tool;

import dev.langchain4j.data.message.UserMessage;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

public class ToolProviderRequest {

    private final Object chatMemoryId;
    private final UserMessage userMessage;

    public ToolProviderRequest(Object chatMemoryId, UserMessage userMessage) {
        this.chatMemoryId = ensureNotNull(chatMemoryId, "chatMemoryId");
        this.userMessage = ensureNotNull(userMessage, "userMessage");
    }

    public Object chatMemoryId() {
        return chatMemoryId;
    }

    public UserMessage userMessage() {
        return userMessage;
    }
}
