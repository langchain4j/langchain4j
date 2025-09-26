package dev.langchain4j.observability.api.event;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailRequest;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.observability.event.DefaultInputGuardrailExecutedEvent;

/**
 * Represents an event that is triggered upon the execution of an input guardrail validation.
 * This interface extends {@code GuardrailExecutedEvent} and ties specifically to input-based
 * guardrail validations.
 *
 * It provides methods to access information such as the request details, result, and the
 * associated input guardrail, all encapsulating the logic for validating input interactions
 * within the system.
 */
public interface InputGuardrailExecutedEvent
        extends GuardrailExecutedEvent<InputGuardrailRequest, InputGuardrailResult, InputGuardrail> {

    /**
     * Retrieves a rewritten user message if a successful rewritten result exists.
     * If the result contains a rewritten message, it constructs a new user message
     * with the rewritten text; otherwise, it returns the original user message.
     *
     * @return The rewritten user message if a rewritten result exists; otherwise, the original user message.
     */
    UserMessage rewrittenUserMessage();

    @Override
    default Class<InputGuardrailExecutedEvent> eventClass() {
        return InputGuardrailExecutedEvent.class;
    }

    @Override
    default InputGuardrailExecutedEventBuilder toBuilder() {
        return new InputGuardrailExecutedEventBuilder(this);
    }

    static InputGuardrailExecutedEventBuilder builder() {
        return new InputGuardrailExecutedEventBuilder();
    }

    /**
     * Builder for {@link InputGuardrailExecutedEvent} instances.
     */
    class InputGuardrailExecutedEventBuilder
            extends GuardrailExecutedEventBuilder<
                    InputGuardrailRequest, InputGuardrailResult, InputGuardrail, InputGuardrailExecutedEvent> {
        protected InputGuardrailExecutedEventBuilder() {}

        /**
         * Creates a builder initialized from an existing {@link InputGuardrailExecutedEvent}.
         */
        protected InputGuardrailExecutedEventBuilder(InputGuardrailExecutedEvent src) {
            super(src);
        }

        /**
         * Builds a {@link InputGuardrailExecutedEvent}.
         */
        @Override
        public InputGuardrailExecutedEvent build() {
            return new DefaultInputGuardrailExecutedEvent(this);
        }
    }
}
