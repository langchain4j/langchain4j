package dev.langchain4j.service.tool;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import java.util.List;
import java.util.Objects;

/**
 * Context for the BeforeToolExecutionHandler that is passed to the handler before executing one or multiple tools.
 * It contains a list of tool execution requests that are about to be executed.
 */
public class BeforeToolsExecutionContext {
    private final List<ToolExecutionRequest> toolExecutionRequests;

    private BeforeToolExecutionContext(Builder builder) {
        this.toolExecutionRequests = copy(builder.toolExecutionRequests);
    }

    /**
     * Returns the list of tool execution requests that are about to be executed.
     *
     * @return the list of tool execution requests
     */
    public List<ToolExecutionRequest> toolExecutionRequests() {
        return toolExecutionRequests;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        BeforeToolExecutionContext that = (BeforeToolExecutionContext) obj;
        return Objects.equals(toolExecutionRequests, that.toolExecutionRequests);
    }

    @Override
    public String toString() {
        return "BeforeToolExecutionContext {" + " toolExecutionRequests = " + toolExecutionRequests + " }";
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(toolExecutionRequests);
        return h;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private List<ToolExecutionRequest> toolExecutionRequests;

        private Builder() {}

        public Builder toolExecutionRequests(List<ToolExecutionRequest> toolExecutionRequests) {
            this.toolExecutionRequests = toolExecutionRequests;
            return this;
        }

        public BeforeToolExecutionContext build() {
            return new BeforeToolExecutionContext(this);
        }
    }
}
