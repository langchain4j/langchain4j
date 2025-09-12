package dev.langchain4j.audit.api.event;

import dev.langchain4j.audit.event.DefaultLLMInteractionCompleteEvent;
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
public interface LLMInteractionCompleteEvent extends LLMInteractionEvent {
    /**
     * Retrieves the result of the interaction process. The result could be the outcome
     * of the interaction, a processed value, or {@code null} if no result exists.
     */
    Optional<Object> result();

    @Override
    default Class<LLMInteractionCompleteEvent> eventClass() {
        return LLMInteractionCompleteEvent.class;
    }

    static LLMInteractionCompleteEventBuilder builder() {
        return new LLMInteractionCompleteEventBuilder();
    }

    @Override
    default LLMInteractionCompleteEventBuilder toBuilder() {
        return new LLMInteractionCompleteEventBuilder(this);
    }

    /**
     * Builder for {@link DefaultLLMInteractionCompleteEvent} instances.
     */
    class LLMInteractionCompleteEventBuilder extends Builder<LLMInteractionCompleteEvent> {
        private @Nullable Object result;

        protected LLMInteractionCompleteEventBuilder() {}

        /**
         * Creates a builder initialized from an existing {@link LLMInteractionCompleteEvent}.
         */
        protected LLMInteractionCompleteEventBuilder(LLMInteractionCompleteEvent src) {
            super(src);
            result(src.result());
        }

        /**
         * Sets the interaction source.
         */
        public LLMInteractionCompleteEventBuilder interactionSource(InteractionSource interactionSource) {
            return (LLMInteractionCompleteEventBuilder) super.interactionSource(interactionSource);
        }

        /**
         * Sets the result.
         */
        public LLMInteractionCompleteEventBuilder result(@Nullable Object result) {
            this.result = result;
            return this;
        }

        /**
         * Builds a {@link LLMInteractionCompleteEvent}.
         */
        public LLMInteractionCompleteEvent build() {
            return new DefaultLLMInteractionCompleteEvent(this);
        }

        @Nullable
        public Object getResult() {
            return result;
        }
    }
}
