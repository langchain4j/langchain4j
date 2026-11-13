package dev.langchain4j.observability.api.event;

import dev.langchain4j.Experimental;
import dev.langchain4j.guardrail.ToolOutputGuardrail;
import dev.langchain4j.guardrail.ToolOutputGuardrailRequest;
import dev.langchain4j.guardrail.ToolOutputGuardrailResult;
import dev.langchain4j.observability.event.DefaultToolOutputGuardrailExecutedEvent;

/**
 * Represents an event triggered upon the execution of a tool output guardrail.
 * <p>
 *     It is distinct from {@link OutputGuardrailExecutedEvent}: a listener for AI service output guardrails
 *     is not notified of tool output guardrails.
 * </p>
 *
 * @since 1.24.0
 */
@Experimental
public interface ToolOutputGuardrailExecutedEvent
        extends GuardrailExecutedEvent<ToolOutputGuardrailRequest, ToolOutputGuardrailResult, ToolOutputGuardrail> {

    @Override
    default Class<ToolOutputGuardrailExecutedEvent> eventClass() {
        return ToolOutputGuardrailExecutedEvent.class;
    }

    @Override
    default ToolOutputGuardrailExecutedEventBuilder toBuilder() {
        return new ToolOutputGuardrailExecutedEventBuilder(this);
    }

    static ToolOutputGuardrailExecutedEventBuilder builder() {
        return new ToolOutputGuardrailExecutedEventBuilder();
    }

    /**
     * Builder for {@link ToolOutputGuardrailExecutedEvent} instances.
     *
     * @since 1.24.0
     */
    @Experimental
    class ToolOutputGuardrailExecutedEventBuilder
            extends GuardrailExecutedEventBuilder<
                    ToolOutputGuardrailRequest,
                    ToolOutputGuardrailResult,
                    ToolOutputGuardrail,
                    ToolOutputGuardrailExecutedEvent> {
        protected ToolOutputGuardrailExecutedEventBuilder() {}

        /**
         * Creates a builder initialized from an existing {@link ToolOutputGuardrailExecutedEvent}.
         */
        protected ToolOutputGuardrailExecutedEventBuilder(ToolOutputGuardrailExecutedEvent src) {
            super(src);
        }

        /**
         * Builds a {@link ToolOutputGuardrailExecutedEvent}.
         */
        @Override
        public ToolOutputGuardrailExecutedEvent build() {
            return new DefaultToolOutputGuardrailExecutedEvent(this);
        }
    }
}
