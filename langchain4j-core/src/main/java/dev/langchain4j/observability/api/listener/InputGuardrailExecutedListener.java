package dev.langchain4j.observability.api.listener;

import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailRequest;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.observability.api.event.InputGuardrailExecutedEvent;

/**
 * A specialized listener interface for handling events of type {@link InputGuardrailExecutedEvent},
 * which are triggered upon the execution of input guardrail validations. This listener provides
 * functionality specific to input-based guardrail execution, including access to the corresponding
 * input request, result, and guardrail implementation.
 */
@FunctionalInterface
public non-sealed interface InputGuardrailExecutedListener
        extends GuardrailExecutedListener<
                InputGuardrailExecutedEvent, InputGuardrailRequest, InputGuardrailResult, InputGuardrail> {
    @Override
    default Class<InputGuardrailExecutedEvent> getEventClass() {
        return InputGuardrailExecutedEvent.class;
    }
}
