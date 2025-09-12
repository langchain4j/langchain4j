package dev.langchain4j.audit.api.event;

import dev.langchain4j.audit.event.DefaultLLMResponseReceivedEvent;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * Invoked with a response from an LLM. It is important to note that this can be invoked multiple times during a single LLM interaction
 * when tools or guardrails exist.
 */
public interface LLMResponseReceivedEvent extends LLMInteractionEvent {
    /**
     * Retrieves the chat response from the LLM interaction event.
     *
     * @return the {@link ChatResponse} object containing the AI-generated message and related metadata.
     */
    ChatResponse response();

    @Override
    default Class<LLMResponseReceivedEvent> eventClass() {
        return LLMResponseReceivedEvent.class;
    }

    @Override
    default LLMResponseReceivedEventBuilder toBuilder() {
        return new LLMResponseReceivedEventBuilder(this);
    }

    static LLMResponseReceivedEventBuilder builder() {
        return new LLMResponseReceivedEventBuilder();
    }

    /**
     * Builder for {@link DefaultLLMResponseReceivedEvent} instances.
     */
    class LLMResponseReceivedEventBuilder extends Builder<LLMResponseReceivedEvent> {
        private ChatResponse response;

        protected LLMResponseReceivedEventBuilder() {}

        /**
         * Creates a builder initialized from an existing {@link LLMResponseReceivedEvent}.
         */
        protected LLMResponseReceivedEventBuilder(LLMResponseReceivedEvent src) {
            super(src);
            response(src.response());
        }

        public ChatResponse getResponse() {
            return response;
        }

        /**
         * Sets the interaction source.
         */
        public LLMResponseReceivedEventBuilder interactionSource(InteractionSource interactionSource) {
            return (LLMResponseReceivedEventBuilder) super.interactionSource(interactionSource);
        }

        /**
         * Sets the chat response.
         */
        public LLMResponseReceivedEventBuilder response(ChatResponse response) {
            this.response = response;
            return this;
        }

        /**
         * Builds a {@link LLMResponseReceivedEvent}.
         */
        public LLMResponseReceivedEvent build() {
            return new DefaultLLMResponseReceivedEvent(this);
        }
    }
}
