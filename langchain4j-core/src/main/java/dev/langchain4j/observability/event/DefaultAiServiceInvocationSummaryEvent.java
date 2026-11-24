package dev.langchain4j.observability.event;

import dev.langchain4j.observability.api.event.AiServiceEvent;
import dev.langchain4j.observability.api.event.AiServiceInvocationSummaryEvent;
import java.util.List;

/**
 * Default implementation of {@link AiServiceInvocationSummaryEvent}.
 */
public class DefaultAiServiceInvocationSummaryEvent extends AbstractAiServiceEvent implements AiServiceInvocationSummaryEvent {

    private final List<AiServiceEvent> events;

    public DefaultAiServiceInvocationSummaryEvent(AiServiceInvocationSummaryEventBuilder builder) {
        super(builder);
        this.events = builder.events();
    }

    @Override
    public List<AiServiceEvent> events() {
        return this.events;
    }
}
