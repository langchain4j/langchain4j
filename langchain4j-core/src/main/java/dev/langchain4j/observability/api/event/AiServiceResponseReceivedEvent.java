package dev.langchain4j.observability.api.event;

import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.observability.event.DefaultAiServiceResponseReceivedEvent;

/**
 * Invoked when response from a {@link dev.langchain4j.model.chat.ChatModel} is received.
 * It is important to note that this can be invoked multiple times during a single AI Service invocation
 * when tools or guardrails exist.
 */
public interface AiServiceResponseReceivedEvent extends AiServiceEvent {
    /**
     * Retrieves the chat response from the AI Service invocation event.
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

        public ChatResponse response() {
            return response;
        }

        /**
         * Sets the invocation context.
         */
        public AiServiceResponseReceivedEventBuilder invocationContext(InvocationContext invocationContext) {
            return (AiServiceResponseReceivedEventBuilder) super.invocationContext(invocationContext);
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
