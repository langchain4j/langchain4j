package dev.langchain4j.audit.api.event;

import dev.langchain4j.audit.event.DefaultAiServiceResponseReceivedEvent;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * Invoked with a response from an LLM. It is important to note that this can be invoked multiple times during a single LLM interaction
 * when tools or guardrails exist.
 */
public interface AiServiceResponseReceivedEvent extends AiServiceInteractionEvent {
    /**
     * Retrieves the chat response from the LLM interaction event.
     *
     * @return the {@link ChatResponse} object containing the AI-generated message and related metadata.
     */
    ChatResponse response();

    @Override
    default Class<AiServiceResponseReceivedEvent> eventClass() {
        return AiServiceResponseReceivedEvent.class;
    }

    @Override
    default AiServiceResponseReceivedEventBuilder toBuilder() {
        return new AiServiceResponseReceivedEventBuilder(this);
    }

    static AiServiceResponseReceivedEventBuilder builder() {
        return new AiServiceResponseReceivedEventBuilder();
    }

    /**
     * Builder for {@link DefaultAiServiceResponseReceivedEvent} instances.
     */
    class AiServiceResponseReceivedEventBuilder extends Builder<AiServiceResponseReceivedEvent> {
        private ChatResponse response;

        protected AiServiceResponseReceivedEventBuilder() {}

        /**
         * Creates a builder initialized from an existing {@link AiServiceResponseReceivedEvent}.
         */
        protected AiServiceResponseReceivedEventBuilder(AiServiceResponseReceivedEvent src) {
            super(src);
            response(src.response());
        }

        public ChatResponse getResponse() {
            return response;
        }

        /**
         * Sets the interaction source.
         */
        public AiServiceResponseReceivedEventBuilder interactionSource(InteractionSource interactionSource) {
            return (AiServiceResponseReceivedEventBuilder) super.interactionSource(interactionSource);
        }

        /**
         * Sets the chat response.
         */
        public AiServiceResponseReceivedEventBuilder response(ChatResponse response) {
            this.response = response;
            return this;
        }

        /**
         * Builds a {@link AiServiceResponseReceivedEvent}.
         */
        public AiServiceResponseReceivedEvent build() {
            return new DefaultAiServiceResponseReceivedEvent(this);
        }
    }
}
