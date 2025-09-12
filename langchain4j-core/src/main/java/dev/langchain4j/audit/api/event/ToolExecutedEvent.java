package dev.langchain4j.audit.api.event;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.audit.event.DefaultToolExecutedEvent;

/**
 * Invoked with a tool response from an LLM. It is important to note that this can be invoked multiple times within a single llm interaction.
 */
public interface ToolExecutedEvent extends LLMInteractionEvent {
    /**
     * Gets the {@link ToolExecutionRequest} that initiated the tool execution.
     */
    ToolExecutionRequest request();

    /**
     * Gets the result of the tool execution
     */
    String result();

    /**
     * Creates a new builder instance for constructing a {@link ToolExecutedEvent}.
     */
    static ToolExecutedEventBuilder builder() {
        return new ToolExecutedEventBuilder();
    }

    @Override
    default Class<ToolExecutedEvent> eventClass() {
        return ToolExecutedEvent.class;
    }

    @Override
    default ToolExecutedEventBuilder toBuilder() {
        return new ToolExecutedEventBuilder(this);
    }

    class ToolExecutedEventBuilder extends Builder<ToolExecutedEvent> {
        private ToolExecutionRequest request;
        private String result;

        protected ToolExecutedEventBuilder() {}

        /**
         * Creates a builder initialized from an existing {@link ToolExecutedEvent}.
         */
        protected ToolExecutedEventBuilder(ToolExecutedEvent src) {
            super(src);
            request(src.request());
            result(src.result());
        }

        /**
         * Sets the tool execution request.
         */
        public ToolExecutedEventBuilder request(ToolExecutionRequest request) {
            this.request = request;
            return this;
        }

        /**
         * Sets the tool execution result.
         */
        public ToolExecutedEventBuilder result(String result) {
            this.result = result;
            return this;
        }

        public ToolExecutionRequest getRequest() {
            return request;
        }

        public String getResult() {
            return result;
        }

        @Override
        public ToolExecutedEventBuilder interactionSource(InteractionSource interactionSource) {
            return (ToolExecutedEventBuilder) super.interactionSource(interactionSource);
        }

        /**
         * Builds a {@link ToolExecutedEvent}.
         */
        public ToolExecutedEvent build() {
            return new DefaultToolExecutedEvent(this);
        }
    }
}
