package dev.langchain4j.audit.api.event;

import java.util.Optional;
import dev.langchain4j.audit.event.DefaultAiServiceInteractionCompletedEvent;
import org.jspecify.annotations.Nullable;

/**
 * Represents an event that occurs upon the completion of an interaction
 * with a large language model (LLM). This interface extends {@link AiServiceInteractionEvent}
 * to include additional information about the result of the interaction.
 *
 * Classes implementing this interface are expected to provide details
 * on what constitutes the result of an interaction, which may vary based
 * on the specifics of the use case. The result can represent the outcome
 * of the interaction process, a returned value, a processed value, or
 * {@code null} if no meaningful result exists.
 */
public interface AiServiceInteractionCompletedEvent extends AiServiceInteractionEvent {
    /**
     * Retrieves the result of the interaction process. The result could be the outcome
     * of the interaction, a processed value, or {@code null} if no result exists.
     */
    Optional<Object> result();

    @Override
    default Class<AiServiceInteractionCompletedEvent> eventClass() {
        return AiServiceInteractionCompletedEvent.class;
    }

    static AiServiceInteractionCompletedEventBuilder builder() {
        return new AiServiceInteractionCompletedEventBuilder();
    }

    @Override
    default AiServiceInteractionCompletedEventBuilder toBuilder() {
        return new AiServiceInteractionCompletedEventBuilder(this);
    }

    /**
     * Builder for {@link DefaultAiServiceInteractionCompletedEvent} instances.
     */
    class AiServiceInteractionCompletedEventBuilder extends Builder<AiServiceInteractionCompletedEvent> {
        private @Nullable Object result;

        protected AiServiceInteractionCompletedEventBuilder() {}

        /**
         * Creates a builder initialized from an existing {@link AiServiceInteractionCompletedEvent}.
         */
        protected AiServiceInteractionCompletedEventBuilder(AiServiceInteractionCompletedEvent src) {
            super(src);
            result(src.result());
        }

        /**
         * Sets the interaction source.
         */
        public AiServiceInteractionCompletedEventBuilder invocationContext(AiServiceInvocationContext invocationContext) {
            return (AiServiceInteractionCompletedEventBuilder) super.invocationContext(invocationContext);
        }

        /**
         * Sets the result.
         */
        public AiServiceInteractionCompletedEventBuilder result(@Nullable Object result) {
            this.result = result;
            return this;
        }

        /**
         * Builds a {@link AiServiceInteractionCompletedEvent}.
         */
        public AiServiceInteractionCompletedEvent build() {
            return new DefaultAiServiceInteractionCompletedEvent(this);
        }

        @Nullable
        public Object getResult() {
            return result;
        }
    }
}
