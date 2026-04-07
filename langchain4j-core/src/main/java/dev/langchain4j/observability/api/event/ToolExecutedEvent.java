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

        protected ToolExecutedEventBuilder() {}

        /**
         * Creates a builder initialized from an existing {@link ToolExecutedEvent}.
         */
        protected ToolExecutedEventBuilder(ToolExecutedEvent src) {
            super(src);
            request(src.request());
            resultText(src.resultText());
            resultContents(src.resultContents());
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

        public ToolExecutionRequest request() {
            return request;
        }

        public String resultText() {
            return resultText;
        }

        public List<Content> resultContents() {
            return resultContents;
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
