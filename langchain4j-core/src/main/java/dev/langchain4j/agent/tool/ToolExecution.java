package dev.langchain4j.agent.tool;

import java.util.Objects;

import static dev.langchain4j.internal.Utils.quoted;

/**
 * Represents the execution of a tool, including the request and the result.
 */
public class ToolExecution {
    private final ToolExecutionRequest toolExecutionRequest;
    private final String toolExecutionResult;

    /**
     * Creates a {@link ToolExecution} from a {@link Builder}.
     * @param builder the builder.
     */
    private ToolExecution(Builder builder) {
        this.toolExecutionRequest = builder.toolExecutionRequest;
        this.toolExecutionResult = builder.toolExecutionResult;
    }

    /**
     * Returns the request of the tool execution.
     * @return the request of the tool execution.
     */
    public ToolExecutionRequest request() {
        return toolExecutionRequest;
    }

    /**
     * Returns the result of the tool execution.
     * @return the result of the tool execution.
     */
    public String toolExecutionResult() {
        return toolExecutionResult;
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof ToolExecution
                && equalTo((ToolExecution) another);
    }

    private boolean equalTo(ToolExecution another) {
        return Objects.equals(toolExecutionRequest, another.toolExecutionRequest)
                && Objects.equals(toolExecutionResult, another.toolExecutionResult);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(toolExecutionRequest);
        h += (h << 5) + Objects.hashCode(toolExecutionResult);
        return h;
    }

    @Override
    public String toString() {
        return "ToolExecution {"
                + " request = " + toolExecutionRequest
                + ", result = " + quoted(toolExecutionResult)
                + " }";
    }

    /**
     * Creates builder to build {@link ToolExecution}.
     * @return created builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * {@code ToolExecution} builder static inner class.
     */
    public static final class Builder {
        private ToolExecutionRequest toolExecutionRequest;
        private String toolExecutionResult;

        /**
         * Creates a builder for {@code ToolExecution}.
         */
        private Builder() {
        }

        /**
         * Sets the {@code toolExecutionRequest}.
         * @param toolExecutionRequest the {@code toolExecutionRequest}
         * @return the {@code Builder}
         */
        public Builder toolExecutionRequest(ToolExecutionRequest toolExecutionRequest) {
            this.toolExecutionRequest = toolExecutionRequest;
            return this;
        }

        /**
         * Sets the {@code toolExecutionResult}.
         * @param toolExecutionResult the {@code toolExecutionResult}
         * @return the {@code Builder}
         */
        public Builder toolExecutionResult(String toolExecutionResult) {
            this.toolExecutionResult = toolExecutionResult;
            return this;
        }

        /**
         * Returns a {@code ToolExecution} built from the parameters previously set.
         * @return a {@code ToolExecution}
         */
        public ToolExecution build() {
            return new ToolExecution(this);
        }
    }


}