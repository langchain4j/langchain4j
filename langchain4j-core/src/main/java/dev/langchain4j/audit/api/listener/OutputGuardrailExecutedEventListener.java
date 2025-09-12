package dev.langchain4j.audit.api.listener;

import dev.langchain4j.audit.api.event.OutputGuardrailExecutedEvent;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;

/**
 * An event listener specifically designed to handle {@link OutputGuardrailExecutedEvent}.
 * This listener provides a mechanism for processing events that occur during the execution
 * of output guardrail validations.
 *
 * The purpose of this interface is to specialize the generic {@link GuardrailExecutedEventListener}
 * for use with output-related guardrail operations. These operations validate outputs from an LLM
 * against predefined criteria, encapsulated within the {@code OutputGuardrail}.
 */
@FunctionalInterface
public non-sealed interface OutputGuardrailExecutedEventListener
        extends GuardrailExecutedEventListener<
                OutputGuardrailExecutedEvent, OutputGuardrailRequest, OutputGuardrailResult, OutputGuardrail> {
    @Override
    default Class<OutputGuardrailExecutedEvent> getEventClass() {
        return OutputGuardrailExecutedEvent.class;
    }
}
