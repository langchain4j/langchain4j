package dev.langchain4j.service.tool;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import java.util.Objects;
import dev.langchain4j.InvocationContext;
import dev.langchain4j.agent.tool.ToolExecutionRequest;

/**
 * @since 1.4.0
 */
public class ToolErrorContext {

    private final ToolExecutionRequest toolExecutionRequest;
    private final InvocationContext invocationContext;

    public ToolErrorContext(Builder builder) {
        this.toolExecutionRequest = ensureNotNull(builder.toolExecutionRequest, "toolExecutionRequest");
        this.invocationContext = ensureNotNull(builder.invocationContext, "invocationContext");
    }

    public ToolExecutionRequest toolExecutionRequest() {
        return toolExecutionRequest;
    }

    public InvocationContext invocationContext() {
        return invocationContext;
    }

    public Object memoryId() {
        return invocationContext.chatMemoryId();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        ToolErrorContext that = (ToolErrorContext) object;
        return Objects.equals(toolExecutionRequest, that.toolExecutionRequest)
                && Objects.equals(invocationContext, that.invocationContext);
    }

    @Override
    public int hashCode() {
        return Objects.hash(toolExecutionRequest, invocationContext);
    }

    @Override
    public String toString() {
        return "ToolErrorContext{" +
                "toolExecutionRequest=" + toolExecutionRequest +
                ", invocationContext=" + invocationContext +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private ToolExecutionRequest toolExecutionRequest;
        private InvocationContext invocationContext;

        public Builder toolExecutionRequest(ToolExecutionRequest toolExecutionRequest) {
            this.toolExecutionRequest = toolExecutionRequest;
            return this;
        }

        public Builder invocationContext(InvocationContext invocationContext) {
            this.invocationContext = invocationContext;
            return this;
        }

        /**
         * @deprecated Please set {@link #invocationContext(InvocationContext)} instead
         */
        @Deprecated(since = "1.5.0")
        public Builder memoryId(Object memoryId) {
            this.invocationContext = InvocationContext.builder()
                    .chatMemoryId(memoryId)
                    .build();
            return this;
        }

        public ToolErrorContext build() {
            return new ToolErrorContext(this);
        }
    }
}
