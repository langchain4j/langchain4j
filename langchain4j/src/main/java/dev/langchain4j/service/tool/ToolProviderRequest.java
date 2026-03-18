package dev.langchain4j.service.tool;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;

import java.util.List;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

public class ToolProviderRequest {

    private final InvocationContext invocationContext;
    private final UserMessage userMessage;
    private final List<ChatMessage> messages; // TODO names: chatMemory?

    /**
     * @since 1.6.0
     */
    public ToolProviderRequest(Builder builder) {
        this.invocationContext = ensureNotNull(builder.invocationContext, "invocationContext");
        this.userMessage = ensureNotNull(builder.userMessage, "userMessage");
        this.messages = copy(builder.messages);
    }

    public ToolProviderRequest(Object chatMemoryId, UserMessage userMessage) {
        this.invocationContext = InvocationContext.builder()
                .chatMemoryId(chatMemoryId)
                .build();
        this.userMessage = ensureNotNull(userMessage, "userMessage");
        this.messages = List.of();
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

    /**
     * TODO explain how it is different from list of messages
     */
    public UserMessage userMessage() {
        return userMessage;
    }

    public Object chatMemoryId() {
        return invocationContext.chatMemoryId();
    }

    /**
     * Returns the current conversation messages.
     * <p>
     * This is primarily useful for {@linkplain ToolProvider#isDynamic() dynamic} tool providers
     * that need to inspect conversation state to decide which tools to provide.
     *
     * @since 1.13.0
     */
    public List<ChatMessage> messages() {
        return messages;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private InvocationContext invocationContext;
        private UserMessage userMessage;
        private List<ChatMessage> messages;

        public Builder invocationContext(InvocationContext invocationContext) {
            this.invocationContext = invocationContext;
            return this;
        }

        public Builder userMessage(UserMessage userMessage) {
            this.userMessage = userMessage;
            return this;
        }

        /**
         * @since 1.13.0
         */
        public Builder messages(List<ChatMessage> messages) {
            this.messages = messages;
            return this;
        }

        public ToolProviderRequest build() {
            return new ToolProviderRequest(this);
        }
    }
}
