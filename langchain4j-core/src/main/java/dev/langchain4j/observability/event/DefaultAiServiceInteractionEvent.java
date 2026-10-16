package dev.langchain4j.observability.event;

import java.util.List;
import dev.langchain4j.observability.api.event.AiServiceEvent;
import dev.langchain4j.observability.api.event.AiServiceInteractionEvent;

/**
 * Default implementation of {@link AiServiceInteractionEvent}.
 */
public class DefaultAiServiceInteractionEvent extends AbstractAiServiceEvent implements AiServiceInteractionEvent {

    private final List<AiServiceEvent> events;

    public DefaultAiServiceInteractionEvent(AiServiceInteractionEventBuilder builder) {
        super(builder);
        this.events = builder.events();
    }

    @Override
    public List<AiServiceEvent> events() {
        return this.events;
    }
}
