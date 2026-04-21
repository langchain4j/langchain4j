package dev.langchain4j.observability.api.listener;

import dev.langchain4j.observability.api.event.AiServiceInteractionEvent;

/**
 * Listener for AiServiceInteractionEvent, fired once per completed or failed invocation.
 */
public interface AiServiceInteractionListener extends AiServiceListener<AiServiceInteractionEvent> {

    @Override
    default Class<AiServiceInteractionEvent> getEventClass() {
        return AiServiceInteractionEvent.class;
    }
}