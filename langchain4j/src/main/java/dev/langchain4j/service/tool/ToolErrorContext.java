package dev.langchain4j.service.tool;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import java.util.Objects;
import dev.langchain4j.agent.tool.ToolExecutionRequest;

/**
 * @since 1.4.0
 */
public class ToolErrorContext {

    private final ToolExecutionRequest toolExecutionRequest;
    private final Object memoryId;

    public ToolErrorContext(Builder builder) {
        this.toolExecutionRequest = ensureNotNull(builder.toolExecutionRequest, "toolExecutionRequest");
        this.memoryId = builder.memoryId;
    }

    public ToolExecutionRequest toolExecutionRequest() {
        return toolExecutionRequest;
    }

    public Object memoryId() {
        return memoryId;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        ToolErrorContext that = (ToolErrorContext) object;
        return Objects.equals(toolExecutionRequest, that.toolExecutionRequest)
                && Objects.equals(memoryId, that.memoryId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(toolExecutionRequest, memoryId);
    }

    @Override
    public String toString() {
        return "ToolErrorContext{" +
                "toolExecutionRequest=" + toolExecutionRequest +
                ", memoryId=" + memoryId +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private ToolExecutionRequest toolExecutionRequest;
        private Object memoryId;

        public Builder toolExecutionRequest(ToolExecutionRequest toolExecutionRequest) {
            this.toolExecutionRequest = toolExecutionRequest;
            return this;
        }

        public Builder memoryId(Object memoryId) {
            this.memoryId = memoryId;
            return this;
        }

        public ToolErrorContext build() {
            return new ToolErrorContext(this);
        }
    }
}
