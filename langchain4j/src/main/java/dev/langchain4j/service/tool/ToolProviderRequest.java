package dev.langchain4j.service.tool;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.InvocationContext;

public class ToolProviderRequest {

    private final Object chatMemoryId;
    private final UserMessage userMessage;
    private final InvocationContext invocationContext; // TODO name

    /**
     * @since 1.4.0
     */
    public ToolProviderRequest(Builder builder) {
        this.chatMemoryId = ensureNotNull(builder.chatMemoryId, "chatMemoryId");
        this.userMessage = ensureNotNull(builder.userMessage, "userMessage");
        this.invocationContext = ensureNotNull(builder.invocationContext, "invocationContext"); // TODO name
    }

    public ToolProviderRequest(Object chatMemoryId, UserMessage userMessage) {
        this.chatMemoryId = ensureNotNull(chatMemoryId, "chatMemoryId");
        this.userMessage = ensureNotNull(userMessage, "userMessage");
        this.invocationContext = null; // TODO
    }

    public Object chatMemoryId() {
        return chatMemoryId;
    }

    public UserMessage userMessage() {
        return userMessage;
    }

    /**
     * @since 1.4.0
     */
    public InvocationContext invocationContext() { // TODO name
        return invocationContext;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Object chatMemoryId;
        private UserMessage userMessage;
        private InvocationContext invocationContext; // TODO name

        public Builder chatMemoryId(Object chatMemoryId) {
            this.chatMemoryId = chatMemoryId;
            return this;
        }

        public Builder userMessage(UserMessage userMessage) {
            this.userMessage = userMessage;
            return this;
        }

        public Builder invocationContext(InvocationContext invocationContext) { // TODO names
            this.invocationContext = invocationContext;
            return this;
        }

        public ToolProviderRequest build() {
            return new ToolProviderRequest(this);
        }
    }
}
