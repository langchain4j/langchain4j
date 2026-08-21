package dev.langchain4j.observability.api.listener;

import dev.langchain4j.observability.api.event.AiServiceRequestIssuedEvent;

/**
 * A listener for {@link AiServiceRequestIssuedEvent}, which represents an event
 * that occurs just before a request is sent to a large language model (LLM).
 * This interface extends the generic {@link AiServiceListener},
 * specializing it for handling events related to LLM responses.
 */
@FunctionalInterface
public interface AiServiceRequestIssuedListener extends AiServiceListener<AiServiceRequestIssuedEvent> {
    @Override
    default Class<AiServiceRequestIssuedEvent> getEventClass() {
        return AiServiceRequestIssuedEvent.class;
    }
}
