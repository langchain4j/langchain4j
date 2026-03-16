package dev.langchain4j.service.tool;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationParameters;

public class ToolProviderRequest {

    private final InvocationContext invocationContext;
    private final UserMessage userMessage;

    /**
     * @since 1.6.0
     */
    public ToolProviderRequest(Builder builder) {
        this.invocationContext = ensureNotNull(builder.invocationContext, "invocationContext");
        this.userMessage = ensureNotNull(builder.userMessage, "userMessage");
    }

    public ToolProviderRequest(Object chatMemoryId, UserMessage userMessage) {
        this.invocationContext = InvocationContext.builder()
                .chatMemoryId(chatMemoryId)
                .build();
        this.userMessage = ensureNotNull(userMessage, "userMessage");
    }

    /**
     * @since 1.6.0
     */
    public InvocationContext invocationContext() {
        return invocationContext;
    }

    /**
     * @since 1.6.0
     */
    public InvocationParameters invocationParameters() {
        return invocationContext.invocationParameters();
    }

    public UserMessage userMessage() {
        return userMessage;
    }

    public Object chatMemoryId() {
        return invocationContext.chatMemoryId();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private InvocationContext invocationContext;
        private UserMessage userMessage;

        public Builder invocationContext(InvocationContext invocationContext) {
            this.invocationContext = invocationContext;
            return this;
        }

        public Builder userMessage(UserMessage userMessage) {
            this.userMessage = userMessage;
            return this;
        }

        public ToolProviderRequest build() {
            return new ToolProviderRequest(this);
        }
    }
}
