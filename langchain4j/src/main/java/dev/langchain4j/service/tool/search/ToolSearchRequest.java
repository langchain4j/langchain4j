package dev.langchain4j.service.tool.search;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.invocation.InvocationContext;

import java.util.List;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * @since 1.12.0
 */
public class ToolSearchRequest {

    private final ToolExecutionRequest toolSearchRequest; // TODO name
    private final List<ToolSpecification> availableTools;
    private final InvocationContext invocationContext;

    public ToolSearchRequest(Builder builder) {
        this.toolSearchRequest = ensureNotNull(builder.toolSearchRequest, "toolSearchRequest");
        this.availableTools = copy(builder.availableTools); // TODO
        this.invocationContext = ensureNotNull(builder.invocationContext, "invocationContext");
    }

    public ToolExecutionRequest toolSearchRequest() { // TODO names
        return toolSearchRequest;
    }

    public List<ToolSpecification> availableTools() { // TODO names
        return availableTools;
    }

    public InvocationContext invocationContext() {
        return invocationContext;
    }

    // TODO eht

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private ToolExecutionRequest toolSearchRequest;
        private List<ToolSpecification> availableTools;
        private InvocationContext invocationContext;

        public Builder toolSearchRequest(ToolExecutionRequest toolSearchRequest) {
            this.toolSearchRequest = toolSearchRequest;
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
