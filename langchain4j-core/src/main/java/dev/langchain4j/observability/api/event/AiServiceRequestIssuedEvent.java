package dev.langchain4j.observability.api.event;

import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.observability.event.DefaultAiServiceRequestIssuedEvent;
import dev.langchain4j.observability.event.DefaultAiServiceResponseReceivedEvent;

/**
 * Invoked just before a {@link dev.langchain4j.model.chat.ChatModel} is sent.
 * It is important to note that this can be invoked multiple times during a single AI Service invocation
 * when tools or guardrails exist.
 */
public interface AiServiceRequestIssuedEvent extends AiServiceEvent {
    /**
     * Retrieves the chat request from the AI Service invocation event.
     *
     * @return the {@link ChatRequest} object containing the request sent to the LLM.
     */
    ChatRequest request();

    @Override
    default Class<AiServiceRequestIssuedEvent> eventClass() {
        return AiServiceRequestIssuedEvent.class;
    }

    @Override
    default AiServiceRequestIssuedEventBuilder toBuilder() {
        return new AiServiceRequestIssuedEventBuilder(this);
    }

    static AiServiceRequestIssuedEventBuilder builder() {
        return new AiServiceRequestIssuedEventBuilder();
    }

    /**
     * Builder for {@link DefaultAiServiceResponseReceivedEvent} instances.
     */
    class AiServiceRequestIssuedEventBuilder extends Builder<AiServiceRequestIssuedEvent> {
        private ChatRequest request;

        protected AiServiceRequestIssuedEventBuilder() {}

        /**
         * Creates a builder initialized from an existing {@link AiServiceRequestIssuedEvent}.
         */
        protected AiServiceRequestIssuedEventBuilder(AiServiceRequestIssuedEvent src) {
            super(src);
            request(src.request());
        }

        public ChatRequest request() {
            return request;
        }

        /**
         * Sets the invocation context.
         */
        public AiServiceRequestIssuedEventBuilder invocationContext(InvocationContext invocationContext) {
            return (AiServiceRequestIssuedEventBuilder) super.invocationContext(invocationContext);
        }

        /**
         * Sets the chat request.
         */
        public AiServiceRequestIssuedEventBuilder request(ChatRequest request) {
            this.request = request;
            return this;
        }

        /**
         * Builds a {@link AiServiceRequestIssuedEvent}.
         */
        public AiServiceRequestIssuedEvent build() {
            return new DefaultAiServiceRequestIssuedEvent(this);
        }
    }
}
