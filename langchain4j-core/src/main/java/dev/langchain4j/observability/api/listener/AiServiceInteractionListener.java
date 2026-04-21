package dev.langchain4j.observability.api.listener;

import dev.langchain4j.observability.api.event.AiServiceInteractionEvent;

public interface AiServiceInteractionListener extends AiServiceListener<AiServiceInteractionEvent> {

    @Override
    default Class<AiServiceInteractionEvent> getEventClass() {
        return AiServiceInteractionEvent.class;
    }
}