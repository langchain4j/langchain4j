package dev.langchain4j.observability.api.event;

import dev.langchain4j.Experimental;
import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.observability.event.DefaultToolCompensatedEvent;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Invoked after the compensating action of a successfully-executed tool has run to roll the tool back - for example
 * because another tool failed, or because the invocation was cancelled. It carries the tool that was rolled back, its
 * original (successful) result, and the {@link CompensationReason}. It is invoked also when the compensating action
 * failed: then {@link #compensationError()} returns its error, and the effects of the tool may still be in place.
 * Like {@link ToolExecutedEvent}, it can be invoked multiple times within a single AI Service invocation.
 *
 * @since 1.20.0
 */
@Experimental
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
     * @since 1.20.0
     */
    @Experimental
    List<Content> resultContents();

    /**
     * Returns why the tool was compensated.
     */
    CompensationReason reason();

    /**
     * Returns the error of the compensating action, when it failed: the exception the compensating method threw, or
     * the error its returned asynchronous result (for example a {@code CompletableFuture}) completed with. When this
     * is not {@code null}, the tool could not be rolled back, and its effects may still be in place.
     *
     * @return the error of the compensating action, or {@code null} when the tool was rolled back successfully.
     * @throws UnsupportedOperationException if the integration that created this event does not report it. Events
     *                                       built with {@link #builder()} always report it.
     * @since 1.25.0
     */
    @Experimental
    default @Nullable Throwable compensationError() {
        throw new UnsupportedOperationException(
                "This ToolCompensatedEvent does not report whether the compensating action failed: "
                        + "the integration that created it does not set it");
    }

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
        private @Nullable Throwable compensationError;
        private boolean compensationErrorSet;

        protected ToolCompensatedEventBuilder() {}

        /**
         * Creates a builder initialized from an existing {@link ToolCompensatedEvent}. When the event does not report
         * {@link ToolCompensatedEvent#compensationError()} (it throws {@link UnsupportedOperationException}), the
         * builder copies everything else, and {@link #build()} fails until {@link #compensationError(Throwable)} is
         * called.
         */
        protected ToolCompensatedEventBuilder(ToolCompensatedEvent src) {
            super(src);
            request(src.request());
            resultContents(src.resultContents());
            reason(src.reason());
            try {
                compensationError(src.compensationError());
            } catch (UnsupportedOperationException notReported) {
                // left unset: build() fails until compensationError(...) is called
            }
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
         * @since 1.20.0
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

        /**
         * Sets the error of the compensating action: {@code null} when the tool was rolled back successfully,
         * otherwise the exception the compensating action failed with. Required: {@link #build()} fails when it was
         * not called, also when the tool was rolled back successfully.
         * @since 1.25.0
         */
        @Experimental
        public ToolCompensatedEventBuilder compensationError(@Nullable Throwable compensationError) {
            this.compensationError = compensationError;
            this.compensationErrorSet = true;
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

        /**
         * @since 1.25.0
         */
        @Experimental
        public @Nullable Throwable compensationError() {
            return compensationError;
        }

        /**
         * Returns whether {@link #compensationError(Throwable)} was called, also when it was called with {@code null}.
         * Used by {@link DefaultToolCompensatedEvent} to check that it was called.
         * @since 1.25.0
         */
        @Internal
        public boolean isCompensationErrorSet() {
            return compensationErrorSet;
        }

        @Override
        public ToolCompensatedEventBuilder invocationContext(InvocationContext invocationContext) {
            return (ToolCompensatedEventBuilder) super.invocationContext(invocationContext);
        }

        /**
         * Builds a {@link ToolCompensatedEvent}.
         *
         * @throws IllegalArgumentException if neither or both of {@code resultText} and {@code resultContents} are set,
         *                                  or if {@link #compensationError(Throwable)} was not called.
         */
        public ToolCompensatedEvent build() {
            return new DefaultToolCompensatedEvent(this);
        }
    }
}
