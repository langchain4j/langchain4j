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
 * A request to search for tools.
 * Contains a {@link ToolExecutionRequest} representing an LLM tool call (including search terms or a query),
 * as well as all searchable tools in the AI Service.
 *
 * @since 1.12.0
 */
@Experimental
public class ToolSearchRequest {

    private final ToolExecutionRequest toolExecutionRequest;
    private final List<ToolSpecification> searchableTools;
    private final InvocationContext invocationContext;

    public ToolSearchRequest(Builder builder) {
        this.toolExecutionRequest = ensureNotNull(builder.toolExecutionRequest, "toolExecutionRequest");
        this.searchableTools = copy(builder.searchableTools);
        this.invocationContext = ensureNotNull(builder.invocationContext, "invocationContext");
    }

    /**
     * Returns the tool call containing the search query.
     */
    public ToolExecutionRequest toolExecutionRequest() {
        return toolExecutionRequest;
    }

    /**
     * Returns all searchable tools for the AI Service.
     * This list does not include tools that were already found during previous searches.
     */
    public List<ToolSpecification> searchableTools() {
        return searchableTools;
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
                && Objects.equals(searchableTools, that.searchableTools)
                && Objects.equals(invocationContext, that.invocationContext);
    }

    @Override
    public int hashCode() {
        return Objects.hash(toolExecutionRequest, searchableTools, invocationContext);
    }

    @Override
    public String toString() {
        return "ToolSearchRequest{" +
                "toolExecutionRequest=" + toolExecutionRequest +
                ", searchableTools=" + searchableTools +
                ", invocationContext=" + invocationContext +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private ToolExecutionRequest toolExecutionRequest;
        private List<ToolSpecification> searchableTools;
        private InvocationContext invocationContext;

        public Builder toolExecutionRequest(ToolExecutionRequest toolExecutionRequest) {
            this.toolExecutionRequest = toolExecutionRequest;
            return this;
        }

        public Builder searchableTools(List<ToolSpecification> searchableTools) {
            this.searchableTools = searchableTools;
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
