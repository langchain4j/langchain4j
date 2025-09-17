package dev.langchain4j.audit.api.event;

import dev.langchain4j.audit.event.DefaultAiServiceInteractionErrorEvent;

/**
 * Represents an event that occurs when an interaction with a large language model (LLM) fails.
 * This interface extends {@link AiServiceInteractionEvent} to include additional information
 * about the error that caused the failure.
 *
 * Implementers of this interface can provide details about the failure, including the
 * associated {@link Throwable}, which can be used for debugging or logging purposes.
 */
public interface AiServiceInteractionErrorEvent extends AiServiceInteractionEvent {
    /**
     * Retrieves the {@link Throwable} representing the error associated with the LLM interaction failure.
     */
    Throwable error();

    @Override
    default Class<AiServiceInteractionErrorEvent> eventClass() {
        return AiServiceInteractionErrorEvent.class;
    }

    @Override
    default AiServiceInteractionErrorEventBuilder toBuilder() {
        return new AiServiceInteractionErrorEventBuilder(this);
    }

    static AiServiceInteractionErrorEventBuilder builder() {
        return new AiServiceInteractionErrorEventBuilder();
    }

    /**
     * Builder for {@link DefaultAiServiceInteractionErrorEvent} instances.
     */
    class AiServiceInteractionErrorEventBuilder extends Builder<AiServiceInteractionErrorEvent> {
        private Throwable error;

        protected AiServiceInteractionErrorEventBuilder() {}

        /**
         * Creates a builder initialized from an existing {@link AiServiceInteractionErrorEvent}.
         */
        protected AiServiceInteractionErrorEventBuilder(AiServiceInteractionErrorEvent src) {
            super(src);
            error(src.error());
        }

        /**
         * Sets the interaction source.
         */
        public AiServiceInteractionErrorEventBuilder interactionSource(InteractionSource interactionSource) {
            return (AiServiceInteractionErrorEventBuilder) super.interactionSource(interactionSource);
        }

        /**
         * Sets the error.
         */
        public AiServiceInteractionErrorEventBuilder error(Throwable error) {
            this.error = error;
            return this;
        }

        /**
         * Builds a {@link AiServiceInteractionErrorEvent}.
         */
        public AiServiceInteractionErrorEvent build() {
            return new DefaultAiServiceInteractionErrorEvent(this);
        }

        public Throwable getError() {
            return error;
        }
    }
}
