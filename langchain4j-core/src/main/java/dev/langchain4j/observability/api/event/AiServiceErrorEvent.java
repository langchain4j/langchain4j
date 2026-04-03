package dev.langchain4j.observability.api.event;

import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.observability.event.DefaultAiServiceErrorEvent;

/**
 * Represents an event that occurs when an AI Service invocation fails.
 * This interface extends {@link AiServiceEvent} to include additional information
 * about the error that caused the failure.
 *
 * Implementers of this interface can provide details about the failure, including the
 * associated {@link Throwable}, which can be used for debugging or logging purposes.
 */
public interface AiServiceErrorEvent extends AiServiceEvent {
    /**
     * Retrieves the {@link Throwable} representing the error associated with the AI Service invocation failure.
     */
    Throwable error();

    @Override
    default Class<AiServiceErrorEvent> eventClass() {
        return AiServiceErrorEvent.class;
    }

    @Override
    default AiServiceErrorEventBuilder toBuilder() {
        return new AiServiceErrorEventBuilder(this);
    }

    static AiServiceErrorEventBuilder builder() {
        return new AiServiceErrorEventBuilder();
    }

    /**
     * Builder for {@link DefaultAiServiceErrorEvent} instances.
     */
    class AiServiceErrorEventBuilder extends Builder<AiServiceErrorEvent> {
        private Throwable error;

        protected AiServiceErrorEventBuilder() {}

        /**
         * Creates a builder initialized from an existing {@link AiServiceErrorEvent}.
         */
        protected AiServiceErrorEventBuilder(AiServiceErrorEvent src) {
            super(src);
            error(src.error());
        }

        /**
         * Sets the invocation context.
         */
        public AiServiceErrorEventBuilder invocationContext(InvocationContext invocationContext) {
            return (AiServiceErrorEventBuilder) super.invocationContext(invocationContext);
        }

        /**
         * Sets the error.
         */
        public AiServiceErrorEventBuilder error(Throwable error) {
            this.error = error;
            return this;
        }

        /**
         * Builds a {@link AiServiceErrorEvent}.
         */
        public AiServiceErrorEvent build() {
            return new DefaultAiServiceErrorEvent(this);
        }

        public Throwable getError() {
            return error;
        }
    }
}
