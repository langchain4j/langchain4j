package dev.langchain4j.observability.event;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailRequest;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.observability.api.event.InputGuardrailExecutedEvent;

/**
 * Default implementation of {@link InputGuardrailExecutedEvent}.
 */
public class DefaultInputGuardrailExecutedEvent
        extends DefaultGuardrailExecutedEvent<
                InputGuardrailRequest, InputGuardrailResult, InputGuardrail, InputGuardrailExecutedEvent>
        implements InputGuardrailExecutedEvent {

    public DefaultInputGuardrailExecutedEvent(InputGuardrailExecutedEventBuilder builder) {
        super(builder);
    }

    @Override
    public UserMessage rewrittenUserMessage() {
        return result().userMessage(request());
    }
}
