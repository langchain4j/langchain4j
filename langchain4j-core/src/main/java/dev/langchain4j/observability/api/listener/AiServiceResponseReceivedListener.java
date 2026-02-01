package dev.langchain4j.observability.api.listener;

import dev.langchain4j.observability.api.event.AiServiceResponseReceivedEvent;

/**
 * A listener for {@link AiServiceResponseReceivedEvent}, which represents an event
 * that occurs when a response from a large language model (LLM) is received.
 * This interface extends the generic {@link AiServiceListener},
 * specializing it for handling events related to LLM responses.
 *
 * Classes implementing this interface can respond to events where the LLM
 * provides a response during an AI Service invocation, which may happen
 * multiple times if it involves tools or guardrails.
 */
@FunctionalInterface
public interface AiServiceResponseReceivedListener extends AiServiceListener<AiServiceResponseReceivedEvent> {
    @Override
    default Class<AiServiceResponseReceivedEvent> getEventClass() {
        return AiServiceResponseReceivedEvent.class;
    }
}
