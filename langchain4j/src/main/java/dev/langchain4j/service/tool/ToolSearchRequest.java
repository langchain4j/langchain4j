package dev.langchain4j.service.tool;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.invocation.InvocationContext;

import java.util.List;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * @since 1.10.0
 */
public class ToolSearchRequest {

    private final List<ToolSpecification> availableTools;
    private final List<ToolExecutionRequest> toolSearchRequests;
    private final InvocationContext invocationContext;

    public ToolSearchRequest(Builder builder) {
        this.availableTools = copy(builder.availableTools); // TODO
        this.toolSearchRequests = copy(builder.toolSearchRequests); // TODO
        this.invocationContext = ensureNotNull(builder.invocationContext, "invocationContext");
    }

    public List<ToolSpecification> availableTools() { // TODO names
        return availableTools;
    }

    public List<ToolExecutionRequest> toolSearchRequests() { // TODO names
        return toolSearchRequests;
    }

    public InvocationContext invocationContext() {
        return invocationContext;
    }

    // TODO eht

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private List<ToolSpecification> availableTools;
        private List<ToolExecutionRequest> toolSearchRequests;
        private InvocationContext invocationContext;

        public Builder toolSearchRequests(List<ToolExecutionRequest> toolSearchRequests) {
            this.toolSearchRequests = toolSearchRequests;
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
