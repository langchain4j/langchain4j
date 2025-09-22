package dev.langchain4j.audit.api.event;

import dev.langchain4j.audit.event.DefaultAiServiceInvocationErrorEvent;

/**
 * Represents an event that occurs when an interaction with a large language model (LLM) fails.
 * This interface extends {@link AiServiceInvocationEvent} to include additional information
 * about the error that caused the failure.
 *
 * Implementers of this interface can provide details about the failure, including the
 * associated {@link Throwable}, which can be used for debugging or logging purposes.
 */
public interface AiServiceInvocationErrorEvent extends AiServiceInvocationEvent {
    /**
     * Retrieves the {@link Throwable} representing the error associated with the LLM interaction failure.
     */
    Throwable error();

    @Override
    default Class<AiServiceInvocationErrorEvent> eventClass() {
        return AiServiceInvocationErrorEvent.class;
    }

    @Override
    default AiServiceInteractionErrorEventBuilder toBuilder() {
        return new AiServiceInteractionErrorEventBuilder(this);
    }

    static AiServiceInteractionErrorEventBuilder builder() {
        return new AiServiceInteractionErrorEventBuilder();
    }

    /**
     * Builder for {@link DefaultAiServiceInvocationErrorEvent} instances.
     */
    class AiServiceInteractionErrorEventBuilder extends Builder<AiServiceInvocationErrorEvent> {
        private Throwable error;

        protected AiServiceInteractionErrorEventBuilder() {}

        /**
         * Creates a builder initialized from an existing {@link AiServiceInvocationErrorEvent}.
         */
        protected AiServiceInteractionErrorEventBuilder(AiServiceInvocationErrorEvent src) {
            super(src);
            error(src.error());
        }

        /**
         * Sets the interaction source.
         */
        public AiServiceInteractionErrorEventBuilder invocationContext(AiServiceInvocationContext invocationContext) {
            return (AiServiceInteractionErrorEventBuilder) super.invocationContext(invocationContext);
        }

        /**
         * Sets the error.
         */
        public AiServiceInteractionErrorEventBuilder error(Throwable error) {
            this.error = error;
            return this;
        }

        /**
         * Builds a {@link AiServiceInvocationErrorEvent}.
         */
        public AiServiceInvocationErrorEvent build() {
            return new DefaultAiServiceInvocationErrorEvent(this);
        }

        public Throwable getError() {
            return error;
        }
    }
}
