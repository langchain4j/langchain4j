package dev.langchain4j.observability.event;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.observability.api.event.AiServiceEvent;
import dev.langchain4j.observability.api.event.AiServiceInteractionEvent;
import java.util.List;

/**
 * Default implementation of {@link AiServiceInteractionEvent}.
 */
public class DefaultAiServiceInteractionEvent extends AbstractAiServiceEvent implements AiServiceInteractionEvent {

    private final List<AiServiceEvent> events;

    public DefaultAiServiceInteractionEvent(AiServiceInteractionEventBuilder builder) {
        super(builder);
        this.events = List.copyOf(ensureNotNull(builder.events(), "events"));
    }

    @Override
    public List<AiServiceEvent> events() {
        return events;
    }
}
