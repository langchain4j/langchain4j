package dev.langchain4j.service.tool;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import java.util.List;
import java.util.Objects;

/**
 * Represents all tool execution requests of a single tool-calling round, before any of them is executed.
 *
 * @since 1.19.0
 */
@Experimental
public class BeforeAllToolExecutions {

    private final List<ToolExecutionRequest> requests;
    private final InvocationContext invocationContext;

    private BeforeAllToolExecutions(Builder builder) {
        this.requests = copy(ensureNotEmpty(builder.requests, "requests"));
        this.invocationContext = ensureNotNull(builder.invocationContext, "invocationContext");
    }

    /**
     * Returns all tool execution requests of the current tool-calling round,
     * in the order they were requested by the LLM. Never empty.
     *
     * @return the tool execution requests
     */
    public List<ToolExecutionRequest> requests() {
        return requests;
    }

    /**
     * Returns the invocation context of the tool requests that are about to be executed.
     */
    public InvocationContext invocationContext() {
        return invocationContext;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        BeforeAllToolExecutions that = (BeforeAllToolExecutions) obj;
        return Objects.equals(requests, that.requests);
    }

    @Override
    public String toString() {
        return "BeforeAllToolExecutions {" + " requests = " + requests + " }";
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(requests);
        return h;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private List<ToolExecutionRequest> requests;
        private InvocationContext invocationContext;

        private Builder() {}

        public Builder requests(List<ToolExecutionRequest> requests) {
            this.requests = requests;
            return this;
        }

        public Builder invocationContext(InvocationContext invocationContext) {
            this.invocationContext = invocationContext;
            return this;
        }

        public BeforeAllToolExecutions build() {
            return new BeforeAllToolExecutions(this);
        }
    }
}
