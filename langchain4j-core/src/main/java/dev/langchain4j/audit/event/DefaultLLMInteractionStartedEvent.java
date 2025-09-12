package dev.langchain4j.audit.event;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.audit.api.event.LLMInteractionStartedEvent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Default implementation of {@link LLMInteractionStartedEvent}.
 */
public class DefaultLLMInteractionStartedEvent extends AbstractLLMInteractionEvent
        implements LLMInteractionStartedEvent {
    private final @Nullable SystemMessage systemMessage;
    private final UserMessage userMessage;

    public DefaultLLMInteractionStartedEvent(LLMInteractionStartedEventBuilder builder) {
        super(builder);
        this.systemMessage = builder.getSystemMessage();
        this.userMessage = ensureNotNull(builder.getUserMessage(), "userMessage");
    }

    @Override
    public Optional<SystemMessage> systemMessage() {
        return Optional.ofNullable(this.systemMessage);
    }

    @Override
    public UserMessage userMessage() {
        return userMessage;
    }
}
