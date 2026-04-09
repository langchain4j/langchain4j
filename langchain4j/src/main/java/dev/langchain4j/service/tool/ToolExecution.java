package dev.langchain4j.service.tool;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.invocation.InvocationContext;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Represents the execution of a tool, including the request and the result.
 */
public class ToolExecution {

    private final ToolExecutionRequest request;
    private final ToolExecutionResult result;
    private final LocalDateTime startTime;
    private final LocalDateTime finishTime;
    private final InvocationContext invocationContext;

    private ToolExecution(Builder builder) {
        this.request = ensureNotNull(builder.request, "request");
        this.result = ensureNotNull(builder.result, "result");
        this.startTime = builder.startTime;
        this.finishTime = builder.finishTime;
        this.invocationContext = ensureNotNull(builder.invocationContext, "invocationContext");
    }

    /**
     * Returns the request of the tool execution.
     *
     * @return the request of the tool execution.
     */
    public ToolExecutionRequest request() {
        return request;
    }

    /**
     * Returns the tool execution result as text.
     *
     * @return the result of the tool execution.
     * @see #resultContents()
     * @see #resultObject()
     */
    public String result() {
        return result.resultText();
    }

    /**
     * Returns the contents of the tool execution result.
     * For text-only results, returns a singleton list containing a {@link TextContent}.
     *
     * @see #result()
     * @see #resultObject()
     * @since 1.13.0
     */
    @Experimental
    public List<Content> resultContents() {
        return result.resultContents();
    }

    /**
     * Returns the tool execution result as object.
     * This object is the actual value returned by the tool.
     *
     * @return the result of the tool execution.
     * @see #result()
     */
    public Object resultObject() {
        return result.result();
    }

    /**
     * Indicates whether the tool execution result represents an error.
     */
    public boolean hasFailed() {
        return result.isError();
    }

    /**
     * Returns the time when the tool execution started, or {@code null} if not recorded.
     */
    public LocalDateTime startTime() {
        return startTime;
    }

    /**
     * Returns the time when the tool execution finished, or {@code null} if not recorded.
     */
    public LocalDateTime finishTime() {
        return finishTime;
    }

    /**
     * Returns the duration of the tool execution, or {@code null} if timing was not recorded.
     */
    public Duration duration() {
        if (startTime == null || finishTime == null) {
            return null;
        }
        return Duration.between(startTime, finishTime);
    }

    /**
     * Returns the invocation context of the tool execution.
     */
    public InvocationContext invocationContext() {
        return invocationContext;
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        ToolExecution that = (ToolExecution) object;
        return Objects.equals(request, that.request)
                && Objects.equals(result, that.result)
                && Objects.equals(startTime, that.startTime)
                && Objects.equals(finishTime, that.finishTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(request, result, startTime, finishTime);
    }

    @Override
    public String toString() {
        return "ToolExecution{" +
                "request=" + request +
                ", result=" + result +
                ", startTime=" + startTime +
                ", finishTime=" + finishTime +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private ToolExecutionRequest request;
        private ToolExecutionResult result;
        private LocalDateTime startTime;
        private LocalDateTime finishTime;
        private InvocationContext invocationContext;

        private Builder() {
        }

        public Builder request(ToolExecutionRequest request) {
            this.request = request;
            return this;
        }

        public Builder result(ToolExecutionResult result) {
            this.result = result;
            return this;
        }

        public Builder startTime(LocalDateTime startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder finishTime(LocalDateTime finishTime) {
            this.finishTime = finishTime;
            return this;
        }

        public Builder invocationContext(InvocationContext invocationContext) {
            this.invocationContext = invocationContext;
            return this;
        }

        /**
         * @deprecated Please use {@link #result(ToolExecutionResult)} instead
         */
        @Deprecated(since = "1.5.0")
        public Builder result(String result) {
            this.result = ToolExecutionResult.builder()
                    .resultText(result)
                    .build();
            return this;
        }

        public ToolExecution build() {
            return new ToolExecution(this);
        }
    }
}
