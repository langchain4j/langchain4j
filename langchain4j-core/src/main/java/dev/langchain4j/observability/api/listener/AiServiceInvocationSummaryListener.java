package dev.langchain4j.observability.api.listener;

import dev.langchain4j.observability.api.event.AiServiceInvocationSummaryEvent;

/**
 * A listener for {@link AiServiceInvocationSummaryEvent}, which is fired once per AI Service invocation, after it has
 * completed, failed or otherwise ended, and contains all the events fired for that invocation.
 */
@FunctionalInterface
public interface AiServiceInvocationSummaryListener extends AiServiceListener<AiServiceInvocationSummaryEvent> {

    @Override
    default Class<AiServiceInvocationSummaryEvent> getEventClass() {
        return AiServiceInvocationSummaryEvent.class;
    }
}
