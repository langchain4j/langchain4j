package dev.langchain4j.audit.api.event;

import dev.langchain4j.audit.event.DefaultLLMInteractionFailureEvent;

/**
 * Represents an event that occurs when an interaction with a large language model (LLM) fails.
 * This interface extends {@link LLMInteractionEvent} to include additional information
 * about the error that caused the failure.
 *
 * Implementers of this interface can provide details about the failure, including the
 * associated {@link Throwable}, which can be used for debugging or logging purposes.
 */
public interface LLMInteractionFailureEvent extends LLMInteractionEvent {
    /**
     * Retrieves the {@link Throwable} representing the error associated with the LLM interaction failure.
     */
    Throwable error();

    @Override
    default Class<LLMInteractionFailureEvent> eventClass() {
        return LLMInteractionFailureEvent.class;
    }

    @Override
    default LLMInteractionFailureEventBuilder toBuilder() {
        return new LLMInteractionFailureEventBuilder(this);
    }

    static LLMInteractionFailureEventBuilder builder() {
        return new LLMInteractionFailureEventBuilder();
    }

    /**
     * Builder for {@link DefaultLLMInteractionFailureEvent} instances.
     */
    class LLMInteractionFailureEventBuilder extends Builder<LLMInteractionFailureEvent> {
        private Throwable error;

        protected LLMInteractionFailureEventBuilder() {}

        /**
         * Creates a builder initialized from an existing {@link LLMInteractionFailureEvent}.
         */
        protected LLMInteractionFailureEventBuilder(LLMInteractionFailureEvent src) {
            super(src);
            error(src.error());
        }

        /**
         * Sets the interaction source.
         */
        public LLMInteractionFailureEventBuilder interactionSource(InteractionSource interactionSource) {
            return (LLMInteractionFailureEventBuilder) super.interactionSource(interactionSource);
        }

        /**
         * Sets the error.
         */
        public LLMInteractionFailureEventBuilder error(Throwable error) {
            this.error = error;
            return this;
        }

        /**
         * Builds a {@link LLMInteractionFailureEvent}.
         */
        public LLMInteractionFailureEvent build() {
            return new DefaultLLMInteractionFailureEvent(this);
        }

        public Throwable getError() {
            return error;
        }
    }
}
