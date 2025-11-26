package dev.langchain4j.observability.event;

import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.observability.api.event.OutputGuardrailExecutedEvent;

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
