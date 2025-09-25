package dev.langchain4j.audit.api.event;

import dev.langchain4j.audit.event.DefaultAiServiceInvocationErrorEvent;
import dev.langchain4j.invocation.InvocationContext;

/**
 * Represents an event that occurs when an AI Service invocation fails.
 * This interface extends {@link AiServiceInvocationEvent} to include additional information
 * about the error that caused the failure.
 *
 * Implementers of this interface can provide details about the failure, including the
 * associated {@link Throwable}, which can be used for debugging or logging purposes.
 */
public interface AiServiceInvocationErrorEvent extends AiServiceInvocationEvent {
    /**
     * Retrieves the {@link Throwable} representing the error associated with the AI Service invocation failure.
     */
    Throwable error();

    @Override
    default Class<AiServiceInvocationErrorEvent> eventClass() {
        return AiServiceInvocationErrorEvent.class;
    }

    @Override
    default AiServiceInvocationErrorEventBuilder toBuilder() {
        return new AiServiceInvocationErrorEventBuilder(this);
    }

    static AiServiceInvocationErrorEventBuilder builder() {
        return new AiServiceInvocationErrorEventBuilder();
    }

    /**
     * Builder for {@link DefaultAiServiceInvocationErrorEvent} instances.
     */
    class AiServiceInvocationErrorEventBuilder extends Builder<AiServiceInvocationErrorEvent> {
        private Throwable error;

        protected AiServiceInvocationErrorEventBuilder() {}

        /**
         * Creates a builder initialized from an existing {@link AiServiceInvocationErrorEvent}.
         */
        protected AiServiceInvocationErrorEventBuilder(AiServiceInvocationErrorEvent src) {
            super(src);
            error(src.error());
        }

        /**
         * Sets the invocation context.
         */
        public AiServiceInvocationErrorEventBuilder invocationContext(InvocationContext invocationContext) {
            return (AiServiceInvocationErrorEventBuilder) super.invocationContext(invocationContext);
        }

        /**
         * Sets the error.
         */
        public AiServiceInvocationErrorEventBuilder error(Throwable error) {
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
