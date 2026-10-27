package dev.langchain4j.observability.event;

import dev.langchain4j.Experimental;
import dev.langchain4j.observability.api.event.AiServiceCancelledEvent;

/**
 * Default implementation of {@link AiServiceCancelledEvent}.
 *
 * @since 1.23.0
 */
@Experimental
public class DefaultAiServiceCancelledEvent extends AbstractAiServiceEvent implements AiServiceCancelledEvent {

    public DefaultAiServiceCancelledEvent(AiServiceCancelledEventBuilder builder) {
        super(builder);
    }
}
