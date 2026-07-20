package dev.langchain4j.observability.api.event;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.observability.event.DefaultToolCompensatedEvent;

import java.util.List;

/**
 * Invoked after a successfully-executed tool is compensated (rolled back) - for example when another tool in the
 * same round failed, or when the invocation was cancelled. It carries the tool that was rolled back, its original
 * (successful) result, and the {@link CompensationReason}. Like {@link ToolExecutedEvent}, it can be invoked
 * multiple times within a single AI Service invocation.
 *
 * @since 1.19.0
 */
public interface ToolCompensatedEvent extends AiServiceEvent {

    /**
     * Gets the {@link ToolExecutionRequest} of the tool that was compensated.
     */
    ToolExecutionRequest request();

    /**
     * Returns the original (successful) result of the compensated tool as a plain text string.
     *
     * @return the text of the single {@link TextContent} element.
     * @throws IllegalStateException if the result contains non-text or multiple content elements.
     *                               Use {@link #resultContents()} instead.
     */
    String resultText();

    /**
     * Returns the contents of the compensated tool's original (successful) result.
     *
     * @return the list of {@link Content} elements, never {@code null}.
     */
    @Experimental
    List<Content> resultContents();

    /**
     * Returns why the tool was compensated.
     */
    CompensationReason reason();

    /**
     * Creates a new builder instance for constructing a {@link ToolCompensatedEvent}.
     */
    static ToolCompensatedEventBuilder builder() {
        return new ToolCompensatedEventBuilder();
    }

    @Override
    default Class<ToolCompensatedEvent> eventClass() {
        return ToolCompensatedEvent.class;
    }

    @Override
    default ToolCompensatedEventBuilder toBuilder() {
        return new ToolCompensatedEventBuilder(this);
    }

    class ToolCompensatedEventBuilder extends Builder<ToolCompensatedEvent> {

        private ToolExecutionRequest request;
        private String resultText;
        private List<Content> resultContents;
        private CompensationReason reason;

        protected ToolCompensatedEventBuilder() {}

        /**
         * Creates a builder initialized from an existing {@link ToolCompensatedEvent}.
         */
        protected ToolCompensatedEventBuilder(ToolCompensatedEvent src) {
            super(src);
            request(src.request());
            resultContents(src.resultContents());
            reason(src.reason());
        }

        /**
         * Sets the compensated tool's request.
         */
        public ToolCompensatedEventBuilder request(ToolExecutionRequest request) {
            this.request = request;
            return this;
        }

        /**
         * Sets the compensated tool's result text. The text will be wrapped into a single {@link TextContent}.
         * Mutually exclusive with {@link #resultContents(List)}.
         */
        public ToolCompensatedEventBuilder resultText(String resultText) {
            this.resultText = resultText;
            return this;
        }

        /**
         * Sets the compensated tool's result contents.
         * Mutually exclusive with {@link #resultText(String)}.
         */
        @Experimental
        public ToolCompensatedEventBuilder resultContents(List<Content> resultContents) {
            this.resultContents = resultContents;
            return this;
        }

        /**
         * Sets the reason the tool was compensated.
         */
        public ToolCompensatedEventBuilder reason(CompensationReason reason) {
            this.reason = reason;
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

        public CompensationReason reason() {
            return reason;
        }

        @Override
        public ToolCompensatedEventBuilder invocationContext(InvocationContext invocationContext) {
            return (ToolCompensatedEventBuilder) super.invocationContext(invocationContext);
        }

        /**
         * Builds a {@link ToolCompensatedEvent}.
         *
         * @throws IllegalArgumentException if neither or both of {@code resultText} and {@code resultContents} are set.
         */
        public ToolCompensatedEvent build() {
            return new DefaultToolCompensatedEvent(this);
        }
    }
}
