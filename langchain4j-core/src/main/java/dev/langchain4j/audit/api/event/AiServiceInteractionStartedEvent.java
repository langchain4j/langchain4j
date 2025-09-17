package dev.langchain4j.audit.api.event;

import java.util.Optional;
import dev.langchain4j.audit.event.DefaultAiServiceInteractionStartedEvent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.jspecify.annotations.Nullable;

/**
 * Invoked when an LLM interaction has started.
 */
public interface AiServiceInteractionStartedEvent extends AiServiceInteractionEvent {
    /**
     * Retrieves an optional system message associated with the interaction.
     * A system message typically provides instructions regarding the AI's
     * behavior, actions, or response style.
     */
    Optional<SystemMessage> systemMessage();

    /**
     * Retrieves the user message associated with the interaction.
     * The user message represents the content or input provided by the user
     * during the AI interaction.
     */
    UserMessage userMessage();

    /**
     * Creates a new builder instance for constructing a {@link AiServiceInteractionStartedEvent}.
     */
    static AiServiceInteractionStartedEventBuilder builder() {
        return new AiServiceInteractionStartedEventBuilder();
    }

    @Override
    default Class<AiServiceInteractionStartedEvent> eventClass() {
        return AiServiceInteractionStartedEvent.class;
    }

    @Override
    default AiServiceInteractionStartedEventBuilder toBuilder() {
        return new AiServiceInteractionStartedEventBuilder(this);
    }

    /**
     * Builder for {@link DefaultAiServiceInteractionStartedEvent} instances.
     */
    class AiServiceInteractionStartedEventBuilder extends Builder<AiServiceInteractionStartedEvent> {
        private @Nullable SystemMessage systemMessage;
        private UserMessage userMessage;

        protected AiServiceInteractionStartedEventBuilder() {}

        /**
         * Creates a builder initialized from an existing {@link AiServiceInteractionStartedEvent}.
         */
        protected AiServiceInteractionStartedEventBuilder(AiServiceInteractionStartedEvent src) {
            super(src);
            systemMessage(src.systemMessage().orElse(null));
            userMessage(src.userMessage());
        }

        /**
         * Sets the interaction source.
         */
        public AiServiceInteractionStartedEventBuilder interactionSource(InteractionSource interactionSource) {
            return (AiServiceInteractionStartedEventBuilder) super.interactionSource(interactionSource);
        }

        /**
         * Sets a system message.
         */
        public AiServiceInteractionStartedEventBuilder systemMessage(@Nullable SystemMessage systemMessage) {
            this.systemMessage = systemMessage;
            return this;
        }

        /**
         * Sets an optional system message.
         */
        public AiServiceInteractionStartedEventBuilder systemMessage(Optional<SystemMessage> systemMessage) {
            return systemMessage(systemMessage.orElse(null));
        }

        /**
         * Sets the user message.
         */
        public AiServiceInteractionStartedEventBuilder userMessage(UserMessage userMessage) {
            this.userMessage = userMessage;
            return this;
        }

        /**
         * Builds a {@link AiServiceInteractionStartedEvent}.
         */
        public AiServiceInteractionStartedEvent build() {
            return new DefaultAiServiceInteractionStartedEvent(this);
        }

        @Nullable
        public SystemMessage getSystemMessage() {
            return systemMessage;
        }

        public UserMessage getUserMessage() {
            return userMessage;
        }
    }
}
