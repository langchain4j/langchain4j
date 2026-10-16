package dev.langchain4j.observability.api.listener;

import dev.langchain4j.observability.api.event.AiServiceInteractionEvent;

/**
 * A listener for {@link AiServiceInteractionEvent}, which is fired once per AI Service invocation, after it has
 * completed, failed or otherwise ended, and contains all the events fired for that invocation.
 */
@FunctionalInterface
public interface AiServiceInteractionListener extends AiServiceListener<AiServiceInteractionEvent> {

    @Override
    default Class<AiServiceInteractionEvent> getEventClass() {
        return AiServiceInteractionEvent.class;
    }
}
