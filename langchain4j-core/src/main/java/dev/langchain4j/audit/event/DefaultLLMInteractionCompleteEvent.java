package dev.langchain4j.audit.event;

import dev.langchain4j.audit.api.event.LLMInteractionCompleteEvent;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Default implementation of {@link LLMInteractionCompleteEvent}.
 */
public class DefaultLLMInteractionCompleteEvent extends AbstractLLMInteractionEvent
        implements LLMInteractionCompleteEvent {
    private final @Nullable Object result;

    public DefaultLLMInteractionCompleteEvent(LLMInteractionCompleteEventBuilder builder) {
        super(builder);
        this.result = builder.getResult();
    }

    @Override
    public Optional<Object> result() {
        return Optional.ofNullable(result);
    }
}
