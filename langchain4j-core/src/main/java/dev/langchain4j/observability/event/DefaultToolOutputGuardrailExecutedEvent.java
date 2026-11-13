package dev.langchain4j.observability.event;

import dev.langchain4j.Experimental;
import dev.langchain4j.guardrail.ToolOutputGuardrail;
import dev.langchain4j.guardrail.ToolOutputGuardrailRequest;
import dev.langchain4j.guardrail.ToolOutputGuardrailResult;
import dev.langchain4j.observability.api.event.ToolOutputGuardrailExecutedEvent;

/**
 * Default implementation of {@link ToolOutputGuardrailExecutedEvent}.
 *
 * @since 1.24.0
 */
@Experimental
public class DefaultToolOutputGuardrailExecutedEvent
        extends DefaultGuardrailExecutedEvent<
                ToolOutputGuardrailRequest,
                ToolOutputGuardrailResult,
                ToolOutputGuardrail,
                ToolOutputGuardrailExecutedEvent>
        implements ToolOutputGuardrailExecutedEvent {

    public DefaultToolOutputGuardrailExecutedEvent(ToolOutputGuardrailExecutedEventBuilder builder) {
        super(builder);
    }
}
