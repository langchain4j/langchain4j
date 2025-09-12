package dev.langchain4j.audit.event;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.audit.api.event.ToolExecutedEvent;

/**
 * Default implementation of {@link ToolExecutedEvent}.
 */
public class DefaultToolExecutedEvent extends AbstractLLMInteractionEvent implements ToolExecutedEvent {
    private final ToolExecutionRequest request;
    private final String result;

    public DefaultToolExecutedEvent(ToolExecutedEventBuilder builder) {
        super(builder);
        this.request = ensureNotNull(builder.getRequest(), "request");
        this.result = ensureNotNull(builder.getResult(), "result");
    }

    @Override
    public ToolExecutionRequest request() {
        return request;
    }

    @Override
    public String result() {
        return result;
    }
}
