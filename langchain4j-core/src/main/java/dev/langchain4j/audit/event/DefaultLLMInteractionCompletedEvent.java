package dev.langchain4j.audit.event;

import dev.langchain4j.audit.api.event.LLMInteractionCompletedEvent;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Default implementation of {@link LLMInteractionCompletedEvent}.
 */
public class DefaultLLMInteractionCompletedEvent extends AbstractLLMInteractionEvent
        implements LLMInteractionCompletedEvent {
    private final @Nullable Object result;

    public DefaultLLMInteractionCompletedEvent(LLMInteractionCompletedEventBuilder builder) {
        super(builder);
        this.result = builder.getResult();
    }

    @Override
    public Optional<Object> result() {
        return Optional.ofNullable(result);
    }
}
