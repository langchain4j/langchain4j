package dev.langchain4j.audit.event;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.audit.api.event.AiServiceResponseReceivedEvent;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * Default implementation of {@link AiServiceResponseReceivedEvent}.
 */
public class DefaultAiServiceResponseReceivedEvent extends AbstractAiServiceInvocationEvent
        implements AiServiceResponseReceivedEvent {

    private final ChatResponse response;

    public DefaultAiServiceResponseReceivedEvent(AiServiceResponseReceivedEventBuilder builder) {
        super(builder);
        this.response = ensureNotNull(builder.response(), "response");
    }

    @Override
    public ChatResponse response() {
        return response;
    }
}
