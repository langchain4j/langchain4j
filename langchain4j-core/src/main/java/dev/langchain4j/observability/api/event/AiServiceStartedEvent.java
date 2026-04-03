package dev.langchain4j.observability.api.event;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.observability.event.DefaultAiServiceStartedEvent;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Called when an LLM invocation has started.
 */
public interface AiServiceStartedEvent extends AiServiceEvent {
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
     * Creates a new builder instance for constructing a {@link AiServiceStartedEvent}.
     */
    static AiServiceStartedEventBuilder builder() {
        return new AiServiceStartedEventBuilder();
    }

    @Override
    default Class<AiServiceStartedEvent> eventClass() {
        return AiServiceStartedEvent.class;
    }

    @Override
    default AiServiceStartedEventBuilder toBuilder() {
        return new AiServiceStartedEventBuilder(this);
    }

    /**
     * Builder for {@link DefaultAiServiceStartedEvent} instances.
     */
    class AiServiceStartedEventBuilder extends Builder<AiServiceStartedEvent> {
        private @Nullable SystemMessage systemMessage;
        private UserMessage userMessage;

        protected AiServiceStartedEventBuilder() {}

        /**
         * Creates a builder initialized from an existing {@link AiServiceStartedEvent}.
         */
        protected AiServiceStartedEventBuilder(AiServiceStartedEvent src) {
            super(src);
            systemMessage(src.systemMessage().orElse(null));
            userMessage(src.userMessage());
        }

        /**
         * Sets the invocation context.
         */
        public AiServiceStartedEventBuilder invocationContext(InvocationContext invocationContext) {
            return (AiServiceStartedEventBuilder) super.invocationContext(invocationContext);
        }

        /**
         * Sets a system message.
         */
        public AiServiceStartedEventBuilder systemMessage(@Nullable SystemMessage systemMessage) {
            this.systemMessage = systemMessage;
            return this;
        }

        /**
         * Sets an optional system message.
         */
        public AiServiceStartedEventBuilder systemMessage(Optional<SystemMessage> systemMessage) {
            return systemMessage(systemMessage.orElse(null));
        }

        /**
         * Sets the user message.
         */
        public AiServiceStartedEventBuilder userMessage(UserMessage userMessage) {
            this.userMessage = userMessage;
            return this;
        }

        /**
         * Builds a {@link AiServiceStartedEvent}.
         */
        public AiServiceStartedEvent build() {
            return new DefaultAiServiceStartedEvent(this);
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
