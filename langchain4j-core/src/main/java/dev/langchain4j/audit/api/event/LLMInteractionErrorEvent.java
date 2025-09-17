package dev.langchain4j.audit.api.event;

import dev.langchain4j.audit.event.DefaultLLMInteractionErrorEvent;

/**
 * Represents an event that occurs when an interaction with a large language model (LLM) fails.
 * This interface extends {@link LLMInteractionEvent} to include additional information
 * about the error that caused the failure.
 *
 * Implementers of this interface can provide details about the failure, including the
 * associated {@link Throwable}, which can be used for debugging or logging purposes.
 */
public interface LLMInteractionErrorEvent extends LLMInteractionEvent {
    /**
     * Retrieves the {@link Throwable} representing the error associated with the LLM interaction failure.
     */
    Throwable error();

    @Override
    default Class<LLMInteractionErrorEvent> eventClass() {
        return LLMInteractionErrorEvent.class;
    }

    @Override
    default LLMInteractionErrorEventBuilder toBuilder() {
        return new LLMInteractionErrorEventBuilder(this);
    }

    static LLMInteractionErrorEventBuilder builder() {
        return new LLMInteractionErrorEventBuilder();
    }

    /**
     * Builder for {@link DefaultLLMInteractionErrorEvent} instances.
     */
    class LLMInteractionErrorEventBuilder extends Builder<LLMInteractionErrorEvent> {
        private Throwable error;

        protected LLMInteractionErrorEventBuilder() {}

        /**
         * Creates a builder initialized from an existing {@link LLMInteractionErrorEvent}.
         */
        protected LLMInteractionErrorEventBuilder(LLMInteractionErrorEvent src) {
            super(src);
            error(src.error());
        }

        /**
         * Sets the interaction source.
         */
        public LLMInteractionErrorEventBuilder interactionSource(InteractionSource interactionSource) {
            return (LLMInteractionErrorEventBuilder) super.interactionSource(interactionSource);
        }

        /**
         * Sets the error.
         */
        public LLMInteractionErrorEventBuilder error(Throwable error) {
            this.error = error;
            return this;
        }

        /**
         * Builds a {@link LLMInteractionErrorEvent}.
         */
        public LLMInteractionErrorEvent build() {
            return new DefaultLLMInteractionErrorEvent(this);
        }

        public Throwable getError() {
            return error;
        }
    }
}
