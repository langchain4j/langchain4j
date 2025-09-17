package dev.langchain4j.audit.event;

import java.util.Optional;
import dev.langchain4j.audit.api.event.AiServiceInteractionCompletedEvent;
import org.jspecify.annotations.Nullable;

/**
 * Default implementation of {@link AiServiceInteractionCompletedEvent}.
 */
public class DefaultAiServiceInteractionCompletedEvent extends AbstractAiServiceInteractionEvent implements AiServiceInteractionCompletedEvent {
    private final @Nullable Object result;

    public DefaultAiServiceInteractionCompletedEvent(AiServiceInteractionCompletedEventBuilder builder) {
        super(builder);
        this.result = builder.getResult();
    }

    @Override
    public Optional<Object> result() {
        return Optional.ofNullable(result);
    }
}
