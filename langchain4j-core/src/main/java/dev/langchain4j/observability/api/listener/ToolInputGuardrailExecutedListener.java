package dev.langchain4j.observability.api.listener;

import dev.langchain4j.Experimental;
import dev.langchain4j.guardrail.ToolInputGuardrail;
import dev.langchain4j.guardrail.ToolInputGuardrailRequest;
import dev.langchain4j.guardrail.ToolInputGuardrailResult;
import dev.langchain4j.observability.api.event.ToolInputGuardrailExecutedEvent;

/**
 * A listener for {@link ToolInputGuardrailExecutedEvent}s, triggered upon the execution of tool input guardrails.
 *
 * @since 1.24.0
 */
@Experimental
@FunctionalInterface
public non-sealed interface ToolInputGuardrailExecutedListener
        extends GuardrailExecutedListener<
                ToolInputGuardrailExecutedEvent,
                ToolInputGuardrailRequest,
                ToolInputGuardrailResult,
                ToolInputGuardrail> {
    @Override
    default Class<ToolInputGuardrailExecutedEvent> getEventClass() {
        return ToolInputGuardrailExecutedEvent.class;
    }
}
