package dev.langchain4j.observability.event;

import dev.langchain4j.Experimental;
import dev.langchain4j.guardrail.ToolInputGuardrail;
import dev.langchain4j.guardrail.ToolInputGuardrailRequest;
import dev.langchain4j.guardrail.ToolInputGuardrailResult;
import dev.langchain4j.observability.api.event.ToolInputGuardrailExecutedEvent;

/**
 * Default implementation of {@link ToolInputGuardrailExecutedEvent}.
 *
 * @since 1.24.0
 */
@Experimental
public class DefaultToolInputGuardrailExecutedEvent
        extends DefaultGuardrailExecutedEvent<
                ToolInputGuardrailRequest,
                ToolInputGuardrailResult,
                ToolInputGuardrail,
                ToolInputGuardrailExecutedEvent>
        implements ToolInputGuardrailExecutedEvent {

    public DefaultToolInputGuardrailExecutedEvent(ToolInputGuardrailExecutedEventBuilder builder) {
        super(builder);
    }
}
