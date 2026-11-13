package dev.langchain4j.observability.api.event;

import dev.langchain4j.Experimental;
import dev.langchain4j.guardrail.ToolInputGuardrail;
import dev.langchain4j.guardrail.ToolInputGuardrailRequest;
import dev.langchain4j.guardrail.ToolInputGuardrailResult;
import dev.langchain4j.observability.event.DefaultToolInputGuardrailExecutedEvent;

/**
 * Represents an event triggered upon the execution of a tool input guardrail.
 * <p>
 *     It is distinct from {@link InputGuardrailExecutedEvent}: a listener for AI service input guardrails
 *     is not notified of tool input guardrails.
 * </p>
 *
 * @since 1.24.0
 */
@Experimental
public interface ToolInputGuardrailExecutedEvent
        extends GuardrailExecutedEvent<ToolInputGuardrailRequest, ToolInputGuardrailResult, ToolInputGuardrail> {

    @Override
    default Class<ToolInputGuardrailExecutedEvent> eventClass() {
        return ToolInputGuardrailExecutedEvent.class;
    }

    @Override
    default ToolInputGuardrailExecutedEventBuilder toBuilder() {
        return new ToolInputGuardrailExecutedEventBuilder(this);
    }

    static ToolInputGuardrailExecutedEventBuilder builder() {
        return new ToolInputGuardrailExecutedEventBuilder();
    }

    /**
     * Builder for {@link ToolInputGuardrailExecutedEvent} instances.
     *
     * @since 1.24.0
     */
    @Experimental
    class ToolInputGuardrailExecutedEventBuilder
            extends GuardrailExecutedEventBuilder<
                    ToolInputGuardrailRequest,
                    ToolInputGuardrailResult,
                    ToolInputGuardrail,
                    ToolInputGuardrailExecutedEvent> {
        protected ToolInputGuardrailExecutedEventBuilder() {}

        /**
         * Creates a builder initialized from an existing {@link ToolInputGuardrailExecutedEvent}.
         */
        protected ToolInputGuardrailExecutedEventBuilder(ToolInputGuardrailExecutedEvent src) {
            super(src);
        }

        /**
         * Builds a {@link ToolInputGuardrailExecutedEvent}.
         */
        @Override
        public ToolInputGuardrailExecutedEvent build() {
            return new DefaultToolInputGuardrailExecutedEvent(this);
        }
    }
}
