package dev.langchain4j.observability.api.event;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.observability.event.DefaultToolExecutedEvent;
import java.util.List;

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
     * Returns the tool execution result as a plain text string.
     * This is a convenience method for when the result is known to be a single {@link TextContent}.
     *
     * @return the text of the single {@link TextContent} element.
     * @throws IllegalStateException if the result contains non-text or multiple content elements.
     *                               Use {@link #resultContents()} instead.
     */
    String resultText();

    /**
     * Returns the contents of the tool execution result.
     *
     * @return the list of {@link Content} elements, never {@code null}.
     * @since 1.13.0
     */
    @Experimental
    List<Content> resultContents();

    /**
     * Returns whether the tool execution failed, for example because the tool threw an exception
     * and the error was sent to the LLM. It is also {@code true} when the tool error failed the AI Service invocation
     * (for example with {@code ToolExecutionErrorHandler.failInvocation()}): then the result is the error text, which
     * was not sent to the LLM, and an {@link AiServiceErrorEvent} follows. AI Services created with
     * {@code AiServices} always report it. Integrations that create their own events should report it too: code that
     * reads events reflectively, for example a JSON serializer that serializes this event or an
     * {@link AiServiceInvocationSummaryEvent} containing it, fails with {@link UnsupportedOperationException} when it
     * is not reported.
     *
     * @return {@code true} if the tool execution failed, {@code false} if it succeeded
     * @throws UnsupportedOperationException if the integration that created this event does not report it
     * @since 1.23.0
     */
    @Experimental
    default boolean isError() {
        throw new UnsupportedOperationException(
                "This ToolExecutedEvent does not report whether the tool execution failed: "
                        + "the integration that created it does not set it");
    }

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
        private List<Content> resultContents;
        private Boolean isError;

        protected ToolExecutedEventBuilder() {}

        /**
         * Creates a builder initialized from an existing {@link ToolExecutedEvent}.
         */
        protected ToolExecutedEventBuilder(ToolExecutedEvent src) {
            super(src);
            request(src.request());
            resultContents(src.resultContents());
            try {
                isError(src.isError());
            } catch (UnsupportedOperationException notReported) {
                // the copy does not report it either
            }
        }

        /**
         * Sets the tool execution request.
         */
        public ToolExecutedEventBuilder request(ToolExecutionRequest request) {
            this.request = request;
            return this;
        }

        /**
         * Sets the tool execution result text. The text will be wrapped into a single {@link TextContent}.
         * Mutually exclusive with {@link #resultContents(List)}.
         */
        public ToolExecutedEventBuilder resultText(String resultText) {
            this.resultText = resultText;
            return this;
        }

        /**
         * Sets the tool execution result contents.
         * Mutually exclusive with {@link #resultText(String)}.
         *
         * @since 1.13.0
         */
        @Experimental
        public ToolExecutedEventBuilder resultContents(List<Content> resultContents) {
            this.resultContents = resultContents;
            return this;
        }

        /**
         * Sets whether the tool execution failed. When it is not set, or set to {@code null},
         * {@link ToolExecutedEvent#isError()} of the built event throws an {@link UnsupportedOperationException}.
         *
         * @since 1.23.0
         */
        @Experimental
        public ToolExecutedEventBuilder isError(Boolean isError) {
            this.isError = isError;
            return this;
        }

        public ToolExecutionRequest request() {
            return request;
        }

        public String resultText() {
            return resultText;
        }

        public List<Content> resultContents() {
            return resultContents;
        }

        /**
         * Returns whether the tool execution failed, or {@code null} if it was not set.
         *
         * @since 1.23.0
         */
        @Experimental
        public Boolean isError() {
            return isError;
        }

        @Override
        public ToolExecutedEventBuilder invocationContext(InvocationContext invocationContext) {
            return (ToolExecutedEventBuilder) super.invocationContext(invocationContext);
        }

        /**
         * Builds a {@link ToolExecutedEvent}.
         *
         * @throws IllegalArgumentException if neither or both of {@code resultText} and {@code resultContents} are set.
         */
        public ToolExecutedEvent build() {
            return new DefaultToolExecutedEvent(this);
        }
    }
}
