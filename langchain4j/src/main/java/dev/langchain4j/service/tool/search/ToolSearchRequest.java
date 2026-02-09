package dev.langchain4j.service.tool.search;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.invocation.InvocationContext;

import java.util.List;
import java.util.Objects;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * A request for a tool search.
 * Contains a {@link ToolExecutionRequest} representing an LLM tool call,
 * as well as all tools currently available in the AI Service.
 *
 * @since 1.12.0
 */
@Experimental
public class ToolSearchRequest {

    private final ToolExecutionRequest toolExecutionRequest;
    private final List<ToolSpecification> availableTools;
    private final InvocationContext invocationContext;

    public ToolSearchRequest(Builder builder) {
        this.toolExecutionRequest = ensureNotNull(builder.toolExecutionRequest, "toolExecutionRequest");
        this.availableTools = copy(builder.availableTools);
        this.invocationContext = ensureNotNull(builder.invocationContext, "invocationContext");
    }

    /**
     * Returns the tool call containing the search query.
     */
    public ToolExecutionRequest toolExecutionRequest() {
        return toolExecutionRequest;
    }

    /**
     * Returns all tools available in the AI Service.
     */
    public List<ToolSpecification> availableTools() {
        return availableTools;
    }

    /**
     * Returns the AI Service invocation context associated with this tool search request.
     */
    public InvocationContext invocationContext() {
        return invocationContext;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ToolSearchRequest that = (ToolSearchRequest) o;
        return Objects.equals(toolExecutionRequest, that.toolExecutionRequest)
                && Objects.equals(availableTools, that.availableTools)
                && Objects.equals(invocationContext, that.invocationContext);
    }

    @Override
    public int hashCode() {
        return Objects.hash(toolExecutionRequest, availableTools, invocationContext);
    }

    @Override
    public String toString() {
        return "ToolSearchRequest{" +
                "toolExecutionRequest=" + toolExecutionRequest +
                ", availableTools=" + availableTools +
                ", invocationContext=" + invocationContext +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private ToolExecutionRequest toolExecutionRequest;
        private List<ToolSpecification> availableTools;
        private InvocationContext invocationContext;

        public Builder toolExecutionRequest(ToolExecutionRequest toolExecutionRequest) {
            this.toolExecutionRequest = toolExecutionRequest;
            return this;
        }

        public Builder availableTools(List<ToolSpecification> availableTools) {
            this.availableTools = availableTools;
            return this;
        }

        public Builder invocationContext(InvocationContext invocationContext) {
            this.invocationContext = invocationContext;
            return this;
        }

        public ToolSearchRequest build() {
            return new ToolSearchRequest(this);
        }
    }
}
