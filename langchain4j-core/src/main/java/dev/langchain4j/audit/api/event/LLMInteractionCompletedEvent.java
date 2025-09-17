package dev.langchain4j.audit.api.event;

import dev.langchain4j.audit.event.DefaultLLMInteractionCompletedEvent;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Represents an event that occurs upon the completion of an interaction
 * with a large language model (LLM). This interface extends {@link LLMInteractionEvent}
 * to include additional information about the result of the interaction.
 *
 * Classes implementing this interface are expected to provide details
 * on what constitutes the result of an interaction, which may vary based
 * on the specifics of the use case. The result can represent the outcome
 * of the interaction process, a returned value, a processed value, or
 * {@code null} if no meaningful result exists.
 */
public interface LLMInteractionCompletedEvent extends LLMInteractionEvent {
    /**
     * Retrieves the result of the interaction process. The result could be the outcome
     * of the interaction, a processed value, or {@code null} if no result exists.
     */
    Optional<Object> result();

    @Override
    default Class<LLMInteractionCompletedEvent> eventClass() {
        return LLMInteractionCompletedEvent.class;
    }

    static LLMInteractionCompletedEventBuilder builder() {
        return new LLMInteractionCompletedEventBuilder();
    }

    @Override
    default LLMInteractionCompletedEventBuilder toBuilder() {
        return new LLMInteractionCompletedEventBuilder(this);
    }

    /**
     * Builder for {@link DefaultLLMInteractionCompletedEvent} instances.
     */
    class LLMInteractionCompletedEventBuilder extends Builder<LLMInteractionCompletedEvent> {
        private @Nullable Object result;

        protected LLMInteractionCompletedEventBuilder() {}

        /**
         * Creates a builder initialized from an existing {@link LLMInteractionCompletedEvent}.
         */
        protected LLMInteractionCompletedEventBuilder(LLMInteractionCompletedEvent src) {
            super(src);
            result(src.result());
        }

        /**
         * Sets the interaction source.
         */
        public LLMInteractionCompletedEventBuilder interactionSource(InteractionSource interactionSource) {
            return (LLMInteractionCompletedEventBuilder) super.interactionSource(interactionSource);
        }

        /**
         * Sets the result.
         */
        public LLMInteractionCompletedEventBuilder result(@Nullable Object result) {
            this.result = result;
            return this;
        }

        /**
         * Builds a {@link LLMInteractionCompletedEvent}.
         */
        public LLMInteractionCompletedEvent build() {
            return new DefaultLLMInteractionCompletedEvent(this);
        }

        @Nullable
        public Object getResult() {
            return result;
        }
    }
}
