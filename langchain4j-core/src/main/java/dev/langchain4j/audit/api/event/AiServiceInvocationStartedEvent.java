package dev.langchain4j.audit.api.event;

import dev.langchain4j.audit.event.DefaultAiServiceInvocationStartedEvent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.Optional;
import dev.langchain4j.invocation.InvocationContext;
import org.jspecify.annotations.Nullable;

/**
 * Called when an LLM invocation has started.
 */
public interface AiServiceInvocationStartedEvent extends AiServiceInvocationEvent {
    /**
     * Retrieves an optional system message associated with the invocation.
     * A system message typically provides instructions regarding the AI's
     * behavior, actions, or response style.
     */
    Optional<SystemMessage> systemMessage();

    /**
     * Retrieves the user message associated with the invocation.
     * The user message represents the content or input provided by the user
     * during the AI Service invocation.
     */
    UserMessage userMessage();

    /**
     * Creates a new builder instance for constructing a {@link AiServiceInvocationStartedEvent}.
     */
    static AiServiceInvocationStartedEventBuilder builder() {
        return new AiServiceInvocationStartedEventBuilder();
    }

    @Override
    default Class<AiServiceInvocationStartedEvent> eventClass() {
        return AiServiceInvocationStartedEvent.class;
    }

    @Override
    default AiServiceInvocationStartedEventBuilder toBuilder() {
        return new AiServiceInvocationStartedEventBuilder(this);
    }

    /**
     * Builder for {@link DefaultAiServiceInvocationStartedEvent} instances.
     */
    class AiServiceInvocationStartedEventBuilder extends Builder<AiServiceInvocationStartedEvent> {
        private @Nullable SystemMessage systemMessage;
        private UserMessage userMessage;

        protected AiServiceInvocationStartedEventBuilder() {}

        /**
         * Creates a builder initialized from an existing {@link AiServiceInvocationStartedEvent}.
         */
        protected AiServiceInvocationStartedEventBuilder(AiServiceInvocationStartedEvent src) {
            super(src);
            systemMessage(src.systemMessage().orElse(null));
            userMessage(src.userMessage());
        }

        /**
         * Sets the invocation context.
         */
        public AiServiceInvocationStartedEventBuilder invocationContext(InvocationContext invocationContext) {
            return (AiServiceInvocationStartedEventBuilder) super.invocationContext(invocationContext);
        }

        /**
         * Sets a system message.
         */
        public AiServiceInvocationStartedEventBuilder systemMessage(@Nullable SystemMessage systemMessage) {
            this.systemMessage = systemMessage;
            return this;
        }

        /**
         * Sets an optional system message.
         */
        public AiServiceInvocationStartedEventBuilder systemMessage(Optional<SystemMessage> systemMessage) {
            return systemMessage(systemMessage.orElse(null));
        }

        /**
         * Sets the user message.
         */
        public AiServiceInvocationStartedEventBuilder userMessage(UserMessage userMessage) {
            this.userMessage = userMessage;
            return this;
        }

        /**
         * Builds a {@link AiServiceInvocationStartedEvent}.
         */
        public AiServiceInvocationStartedEvent build() {
            return new DefaultAiServiceInvocationStartedEvent(this);
        }

        @Nullable
        public SystemMessage systemMessage() {
            return systemMessage;
        }

        public UserMessage userMessage() {
            return userMessage;
        }
    }
}
