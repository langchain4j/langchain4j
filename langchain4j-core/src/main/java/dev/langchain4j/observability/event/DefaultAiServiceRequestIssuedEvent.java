package dev.langchain4j.observability.event;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.observability.api.event.AiServiceRequestIssuedEvent;

/**
 * Default implementation of {@link AiServiceRequestIssuedEvent}.
 */
public class DefaultAiServiceRequestIssuedEvent extends AbstractAiServiceEvent implements AiServiceRequestIssuedEvent {

    private final ChatRequest request;

    public DefaultAiServiceRequestIssuedEvent(AiServiceRequestIssuedEventBuilder builder) {
        super(builder);
        this.request = ensureNotNull(builder.request(), "request");
    }

    @Override
    public ChatRequest request() {
        return request;
    }
}
