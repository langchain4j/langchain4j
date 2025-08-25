package dev.langchain4j.service.tool;

import java.util.Objects;
import dev.langchain4j.agent.tool.ToolExecutionRequest;

/**
 * Represents the execution of a tool, including the request and the result.
 */
public class ToolExecution {

    private final ToolExecutionRequest request;
    private final ToolExecutionResult result; // TODO document

    private ToolExecution(Builder builder) {
        this.request = builder.request; // TODO
        this.result = builder.result; // TODO
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
     * Returns the result of the tool execution. TODO as text
     *
     * @return the result of the tool execution.
     * @see #resultObject()
     */
    public String result() {
        return result.resultText();
    }

    /**
     * Returns the result object of the tool execution.
     * This is an actual object returned from the tool (not serialized). TODO
     *
     * @return the result of the tool execution.
     * @see #result()
     */
    public Object resultObject() {
        return result.result();
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
        @Deprecated(since = "1.4.0")
        public Builder result(String result) {
            this.result = ToolExecutionResult.builder()
                    .result(result)
                    .build();
            return this;
        }

        public ToolExecution build() {
            return new ToolExecution(this);
        }
    }
}
