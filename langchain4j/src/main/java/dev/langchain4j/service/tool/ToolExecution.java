package dev.langchain4j.service.tool;

import static dev.langchain4j.internal.Utils.quoted;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import java.util.Objects;

/**
 * Represents the execution of a tool, including the request and the result.
 */
public class ToolExecution {

    private final ToolExecutionRequest request;
    private final String result;

    private ToolExecution(Builder builder) {
        this.request = builder.request;
        this.result = builder.result;
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
     * Returns the result of the tool execution.
     *
     * @return the result of the tool execution.
     */
    public String result() {
        return result;
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof ToolExecution && equalTo((ToolExecution) another);
    }

    private boolean equalTo(ToolExecution another) {
        return Objects.equals(request, another.request) && Objects.equals(result, another.result);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(request);
        h += (h << 5) + Objects.hashCode(result);
        return h;
    }

    @Override
    public String toString() {
        return "ToolExecution {" + " request = " + request + ", result = " + quoted(result) + " }";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private ToolExecutionRequest request;
        private String result;

        private Builder() {}

        public Builder request(ToolExecutionRequest request) {
            this.request = request;
            return this;
        }

        public Builder result(String result) {
            this.result = result;
            return this;
        }

        public ToolExecution build() {
            return new ToolExecution(this);
        }
    }
}
