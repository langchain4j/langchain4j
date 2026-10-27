package dev.langchain4j.observability.api.listener;

import dev.langchain4j.Experimental;
import dev.langchain4j.observability.api.event.AiServiceCancelledEvent;

/**
 * A listener for {@link AiServiceCancelledEvent}, which occurs when an AI Service invocation is cancelled before it
 * completes.
 *
 * @since 1.23.0
 */
@FunctionalInterface
@Experimental
public interface AiServiceCancelledListener extends AiServiceListener<AiServiceCancelledEvent> {
    @Override
    default Class<AiServiceCancelledEvent> getEventClass() {
        return AiServiceCancelledEvent.class;
    }
}
