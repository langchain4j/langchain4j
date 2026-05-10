package dev.langchain4j.observability.api.listener;

import dev.langchain4j.observability.api.event.AiServiceInteractionEvent;

/**
 * A listener for {@link AiServiceInteractionEvent}, fired once per AI Service invocation
 * when the interaction has fully completed (successfully or with an error).
 * The event carries the ordered list of all events that occurred during the interaction.
 */
@FunctionalInterface
public interface AiServiceInteractionListener extends AiServiceListener<AiServiceInteractionEvent> {
    @Override
    default Class<AiServiceInteractionEvent> getEventClass() {
        return AiServiceInteractionEvent.class;
    }
}
