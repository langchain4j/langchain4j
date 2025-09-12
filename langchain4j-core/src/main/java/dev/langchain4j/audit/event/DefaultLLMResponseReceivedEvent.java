package dev.langchain4j.audit.event;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.audit.api.event.LLMResponseReceivedEvent;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * Default implementation of {@link LLMResponseReceivedEvent}.
 */
public class DefaultLLMResponseReceivedEvent extends AbstractLLMInteractionEvent implements LLMResponseReceivedEvent {
    private final ChatResponse response;

    public DefaultLLMResponseReceivedEvent(LLMResponseReceivedEventBuilder builder) {
        super(builder);
        this.response = ensureNotNull(builder.getResponse(), "response");
    }

    @Override
    public ChatResponse response() {
        return response;
    }
}
