package dev.langchain4j.observability.api.event;

import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.observability.event.DefaultAiServiceCompletedEvent;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Represents an event that occurs upon the completion of an invocation.
 * This interface extends {@link AiServiceEvent}
 * to include additional information about the result of the invocation.
 *
 * Classes implementing this interface are expected to provide details
 * on what constitutes the result of an invocation, which may vary based
 * on the specifics of the use case. The result can represent the outcome
 * of the invocation process, a returned value, a processed value, or
 * {@code null} if no meaningful result exists.
 */
public interface AiServiceCompletedEvent extends AiServiceEvent {
    /**
     * Retrieves the result of the invocation. The result could be the outcome
     * of the invocation, a processed value, or {@code null} if no result exists.
     */
    Optional<Object> result();

    @Override
    default Class<AiServiceCompletedEvent> eventClass() {
        return AiServiceCompletedEvent.class;
    }

    static AiServiceCompletedEventBuilder builder() {
        return new AiServiceCompletedEventBuilder();
    }

    @Override
    default AiServiceCompletedEventBuilder toBuilder() {
        return new AiServiceCompletedEventBuilder(this);
    }

    /**
     * Builder for {@link DefaultAiServiceCompletedEvent} instances.
     */
    class AiServiceCompletedEventBuilder extends Builder<AiServiceCompletedEvent> {
        private @Nullable Object result;

        protected AiServiceCompletedEventBuilder() {}

        /**
         * Creates a builder initialized from an existing {@link AiServiceCompletedEvent}.
         */
        protected AiServiceCompletedEventBuilder(AiServiceCompletedEvent src) {
            super(src);
            result(src.result());
        }

        /**
         * Sets the invocation context.
         */
        public AiServiceCompletedEventBuilder invocationContext(InvocationContext invocationContext) {
            return (AiServiceCompletedEventBuilder) super.invocationContext(invocationContext);
        }

        /**
         * Sets the result.
         */
        public AiServiceCompletedEventBuilder result(@Nullable Object result) {
            this.result = result;
            return this;
        }

        /**
         * Builds a {@link AiServiceCompletedEvent}.
         */
        public AiServiceCompletedEvent build() {
            return new DefaultAiServiceCompletedEvent(this);
        }

        @Nullable
        public Object getResult() {
            return result;
        }
    }
}
