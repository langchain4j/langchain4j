package dev.langchain4j.audit.event;

import dev.langchain4j.audit.api.event.OutputGuardrailExecutedEvent;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;

/**
 * Default implementation of {@link OutputGuardrailExecutedEvent}.
 */
public class DefaultOutputGuardrailExecutedEvent
        extends DefaultGuardrailExecutedEvent<
                OutputGuardrailRequest, OutputGuardrailResult, OutputGuardrail, OutputGuardrailExecutedEvent>
        implements OutputGuardrailExecutedEvent {
    public DefaultOutputGuardrailExecutedEvent(OutputGuardrailExecutedEventBuilder builder) {
        super(builder);
    }
}
