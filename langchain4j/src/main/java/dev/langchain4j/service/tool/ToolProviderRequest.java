package dev.langchain4j.service.tool;

import dev.langchain4j.data.message.UserMessage;

/**
 * A wrapper mostly used for the {@link ToolProvider}
 */
public class ToolProviderRequest {
    private final Object chatMemoryId;
    private final UserMessage userMessage;

    public ToolProviderRequest(Object chatMemoryId, UserMessage userMessage) {
        this.chatMemoryId = chatMemoryId;
        this.userMessage = userMessage;
    }

    public Object getChatMemoryId() {
        return chatMemoryId;
    }

    public UserMessage getUserMessage() {
        return userMessage;
    }
}