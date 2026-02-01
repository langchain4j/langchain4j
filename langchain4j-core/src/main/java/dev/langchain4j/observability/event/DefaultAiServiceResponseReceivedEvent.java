package dev.langchain4j.observability.event;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.observability.api.event.AiServiceResponseReceivedEvent;

/**
 * Default implementation of {@link AiServiceResponseReceivedEvent}.
 */
public class DefaultAiServiceResponseReceivedEvent extends AbstractAiServiceEvent
        implements AiServiceResponseReceivedEvent {

    private final ChatResponse response;
    private final ChatRequest request;

    public DefaultAiServiceResponseReceivedEvent(AiServiceResponseReceivedEventBuilder builder) {
        super(builder);
        this.response = ensureNotNull(builder.response(), "responses");
        this.request = ensureNotNull(builder.request(), "request");
    }

    @Override
    public ChatResponse response() {
        return response;
    }

    @Override
    public ChatRequest request() {
        return request;
    }
}
