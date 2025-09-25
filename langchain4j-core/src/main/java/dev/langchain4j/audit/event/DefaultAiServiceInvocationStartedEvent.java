package dev.langchain4j.audit.event;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.audit.api.event.AiServiceInvocationStartedEvent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Default implementation of {@link AiServiceInvocationStartedEvent}.
 */
public class DefaultAiServiceInvocationStartedEvent extends AbstractAiServiceInvocationEvent
        implements AiServiceInvocationStartedEvent {

    private final @Nullable SystemMessage systemMessage;
    private final UserMessage userMessage;

    public DefaultAiServiceInvocationStartedEvent(AiServiceInvocationStartedEventBuilder builder) {
        super(builder);
        this.systemMessage = builder.systemMessage();
        this.userMessage = ensureNotNull(builder.userMessage(), "userMessage");
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
