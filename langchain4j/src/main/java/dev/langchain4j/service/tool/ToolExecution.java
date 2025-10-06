package dev.langchain4j.service.tool;

import java.util.Objects;
import dev.langchain4j.agent.tool.ToolExecutionRequest;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Represents the execution of a tool, including the request and the result.
 */
public class ToolExecution {

    private final ToolExecutionRequest request;
    private final ToolExecutionResult result;

    private ToolExecution(Builder builder) {
        this.request = ensureNotNull(builder.request, "request");
        this.result = ensureNotNull(builder.result, "result");
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
     * @see #resultObject()
     */
    public String result() {
        return result.resultText();
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

    @Override
    public boolean equals(final Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        ToolExecution that = (ToolExecution) object;
        return Objects.equals(request, that.request)
                && Objects.equals(result, that.result);
    }

    @Override
    public int hashCode() {
        return Objects.hash(request, result);
    }

    @Override
    public String toString() {
        return "ToolExecution{" +
                "request=" + request +
                ", result=" + result +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private ToolExecutionRequest request;
        private ToolExecutionResult result;

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
