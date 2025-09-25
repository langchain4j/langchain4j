package dev.langchain4j.audit.api.event;

import dev.langchain4j.audit.event.DefaultAiServiceInvocationCompletedEvent;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Represents an event that occurs upon the completion of an invocation.
 * This interface extends {@link AiServiceInvocationEvent}
 * to include additional information about the result of the invocation.
 *
 * Classes implementing this interface are expected to provide details
 * on what constitutes the result of an invocation, which may vary based
 * on the specifics of the use case. The result can represent the outcome
 * of the invocation process, a returned value, a processed value, or
 * {@code null} if no meaningful result exists.
 */
public interface AiServiceInvocationCompletedEvent extends AiServiceInvocationEvent {
    /**
     * Retrieves the result of the invocation. The result could be the outcome
     * of the invocation, a processed value, or {@code null} if no result exists.
     */
    Optional<Object> result();

    @Override
    default Class<AiServiceInvocationCompletedEvent> eventClass() {
        return AiServiceInvocationCompletedEvent.class;
    }

    static AiServiceInvocationCompletedEventBuilder builder() {
        return new AiServiceInvocationCompletedEventBuilder();
    }

    @Override
    default AiServiceInvocationCompletedEventBuilder toBuilder() {
        return new AiServiceInvocationCompletedEventBuilder(this);
    }

    /**
     * Builder for {@link DefaultAiServiceInvocationCompletedEvent} instances.
     */
    class AiServiceInvocationCompletedEventBuilder extends Builder<AiServiceInvocationCompletedEvent> {
        private @Nullable Object result;

        protected AiServiceInvocationCompletedEventBuilder() {}

        /**
         * Creates a builder initialized from an existing {@link AiServiceInvocationCompletedEvent}.
         */
        protected AiServiceInvocationCompletedEventBuilder(AiServiceInvocationCompletedEvent src) {
            super(src);
            result(src.result());
        }

        /**
         * Sets the invocation context.
         */
        public AiServiceInvocationCompletedEventBuilder invocationContext(
                AiServiceInvocationContext invocationContext) {
            return (AiServiceInvocationCompletedEventBuilder) super.invocationContext(invocationContext);
        }

        /**
         * Sets the result.
         */
        public AiServiceInvocationCompletedEventBuilder result(@Nullable Object result) {
            this.result = result;
            return this;
        }

        /**
         * Builds a {@link AiServiceInvocationCompletedEvent}.
         */
        public AiServiceInvocationCompletedEvent build() {
            return new DefaultAiServiceInvocationCompletedEvent(this);
        }

        @Nullable
        public Object getResult() {
            return result;
        }
    }
}
