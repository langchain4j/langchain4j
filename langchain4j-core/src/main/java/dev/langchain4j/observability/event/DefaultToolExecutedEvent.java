package dev.langchain4j.observability.event;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.observability.api.event.ToolExecutedEvent;

/**
 * Default implementation of {@link ToolExecutedEvent}.
 */
public class DefaultToolExecutedEvent extends AbstractAiServiceEvent implements ToolExecutedEvent {

    private final ToolExecutionRequest request;
    private final String result;

    public DefaultToolExecutedEvent(ToolExecutedEventBuilder builder) {
        super(builder);
        this.request = ensureNotNull(builder.request(), "request");
        this.result = ensureNotNull(builder.resultText(), "result");
    }

    @Override
    public ToolExecutionRequest request() {
        return request;
    }

    @Override
    public String resultText() {
        return result;
    }
}
