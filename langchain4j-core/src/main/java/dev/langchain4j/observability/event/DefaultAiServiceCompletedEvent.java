package dev.langchain4j.observability.event;

import dev.langchain4j.observability.api.event.AiServiceCompletedEvent;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Default implementation of {@link AiServiceCompletedEvent}.
 */
public class DefaultAiServiceCompletedEvent extends AbstractAiServiceEvent implements AiServiceCompletedEvent {

    private final @Nullable Object result;

    public DefaultAiServiceCompletedEvent(AiServiceCompletedEventBuilder builder) {
        super(builder);
        this.result = builder.getResult();
    }

    @Override
    public Optional<Object> result() {
        return Optional.ofNullable(result);
    }
}
