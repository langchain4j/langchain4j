package dev.langchain4j.observability.api.listener;

import dev.langchain4j.Experimental;
import dev.langchain4j.guardrail.ToolOutputGuardrail;
import dev.langchain4j.guardrail.ToolOutputGuardrailRequest;
import dev.langchain4j.guardrail.ToolOutputGuardrailResult;
import dev.langchain4j.observability.api.event.ToolOutputGuardrailExecutedEvent;

/**
 * A listener for {@link ToolOutputGuardrailExecutedEvent}s, triggered upon the execution of tool output guardrails.
 *
 * @since 1.24.0
 */
@Experimental
@FunctionalInterface
public non-sealed interface ToolOutputGuardrailExecutedListener
        extends GuardrailExecutedListener<
                ToolOutputGuardrailExecutedEvent,
                ToolOutputGuardrailRequest,
                ToolOutputGuardrailResult,
                ToolOutputGuardrail> {
    @Override
    default Class<ToolOutputGuardrailExecutedEvent> getEventClass() {
        return ToolOutputGuardrailExecutedEvent.class;
    }
}
