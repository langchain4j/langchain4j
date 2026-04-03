package dev.langchain4j.observability.api.event;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.observability.event.DefaultToolExecutedEvent;

/**
 * Invoked after the tool is executed.
 * It is important to note that this can be invoked multiple times within a single AI Service invocation.
 */
public interface ToolExecutedEvent extends AiServiceEvent {
    /**
     * Gets the {@link ToolExecutionRequest} that initiated the tool execution.
     */
    ToolExecutionRequest request();

    /**
     * Gets the result of the tool execution
     */
    String resultText();

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
        private String resultText;

        protected ToolExecutedEventBuilder() {}

        /**
         * Creates a builder initialized from an existing {@link ToolExecutedEvent}.
         */
        protected ToolExecutedEventBuilder(ToolExecutedEvent src) {
            super(src);
            request(src.request());
            resultText(src.resultText());
        }

        /**
         * Sets the tool execution request.
         */
        public ToolExecutedEventBuilder request(ToolExecutionRequest request) {
            this.request = request;
            return this;
        }

        /**
         * Sets the tool execution result text.
         */
        public ToolExecutedEventBuilder resultText(String resultText) {
            this.resultText = resultText;
            return this;
        }

        public ToolExecutionRequest request() {
            return request;
        }

        public String resultText() {
            return resultText;
        }

        @Override
        public ToolExecutedEventBuilder invocationContext(InvocationContext invocationContext) {
            return (ToolExecutedEventBuilder) super.invocationContext(invocationContext);
        }

        /**
         * Builds a {@link ToolExecutedEvent}.
         */
        public ToolExecutedEvent build() {
            return new DefaultToolExecutedEvent(this);
        }
    }
}
