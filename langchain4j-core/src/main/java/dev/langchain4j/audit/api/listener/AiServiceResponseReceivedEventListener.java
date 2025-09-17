package dev.langchain4j.audit.api.listener;

import dev.langchain4j.audit.api.event.AiServiceResponseReceivedEvent;

/**
 * A listener for {@link AiServiceResponseReceivedEvent}, which represents an event
 * that occurs when a response from a large language model (LLM) is received.
 * This interface extends the generic {@link AiServiceInteractionEventListener},
 * specializing it for handling events related to LLM responses.
 *
 * Classes implementing this interface can respond to events where the LLM
 * provides a response during an interaction, which may happen multiple times
 * if the LLM interaction involves tools or guardrails.
 */
@FunctionalInterface
public interface AiServiceResponseReceivedEventListener
        extends AiServiceInteractionEventListener<AiServiceResponseReceivedEvent> {
    @Override
    default Class<AiServiceResponseReceivedEvent> getEventClass() {
        return AiServiceResponseReceivedEvent.class;
    }
}
