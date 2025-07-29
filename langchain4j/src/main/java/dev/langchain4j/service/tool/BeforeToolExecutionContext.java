package dev.langchain4j.service.tool;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import java.util.Objects;

/**
 * Context for the BeforeToolExecutionHandler that is passed to the handler before executing one tool.
 * It contains a tool execution request that is about to be executed.
 */
public class BeforeToolExecutionContext {
    private final ToolExecutionRequest toolExecutionRequest;

    private BeforeToolExecutionContext(Builder builder) {
        this.toolExecutionRequest = builder.toolExecutionRequest;
    }

    /**
     * Returns the tool execution request that is about to be executed.
     *
     * @return the tool execution request
     */
    public ToolExecutionRequest toolExecutionRequest() {
        return toolExecutionRequest;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        BeforeToolExecutionContext that = (BeforeToolExecutionContext) obj;
        return Objects.equals(toolExecutionRequest, that.toolExecutionRequest);
    }

    @Override
    public String toString() {
        return "BeforeToolExecutionContext {" + " toolExecutionRequest = " + toolExecutionRequest + " }";
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(toolExecutionRequest);
        return h;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private ToolExecutionRequest toolExecutionRequest;

        private Builder() {}

        public Builder toolExecutionRequests(ToolExecutionRequest toolExecutionRequest) {
            this.toolExecutionRequest = toolExecutionRequest;
            return this;
        }

        public BeforeToolExecutionContext build() {
            return new BeforeToolExecutionContext(this);
        }
    }
}
