package dev.langchain4j.audit.event;

import java.util.Optional;
import dev.langchain4j.audit.api.event.AiServiceInvocationCompletedEvent;
import org.jspecify.annotations.Nullable;

/**
 * Default implementation of {@link AiServiceInvocationCompletedEvent}.
 */
public class DefaultAiServiceInvocationCompletedEvent extends AbstractAiServiceInvocationEvent
        implements AiServiceInvocationCompletedEvent {
    private final @Nullable Object result;

    public DefaultAiServiceInvocationCompletedEvent(AiServiceInteractionCompletedEventBuilder builder) {
        super(builder);
        this.result = builder.getResult();
    }

    @Override
    public Optional<Object> result() {
        return Optional.ofNullable(result);
    }
}
