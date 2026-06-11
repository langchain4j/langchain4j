package dev.langchain4j.service.tool;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import java.util.Objects;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationParameters;

/**
 * @since 1.4.0
 */
public class ToolErrorContext {

    private final ToolExecutionRequest toolExecutionRequest;
    private final InvocationContext invocationContext;
    private final Exception originalException;

    public ToolErrorContext(Builder builder) {
        this.toolExecutionRequest = ensureNotNull(builder.toolExecutionRequest, "toolExecutionRequest");
        this.invocationContext = ensureNotNull(builder.invocationContext, "invocationContext");
        this.originalException = builder.originalException;
    }

    public ToolExecutionRequest toolExecutionRequest() {
        return toolExecutionRequest;
    }

    /**
     * @since 1.6.0
     */
    public InvocationContext invocationContext() {
        return invocationContext;
    }

    /**
     * @since 1.6.0
     */
    public InvocationParameters invocationParameters() {
        return invocationContext.invocationParameters();
    }

    /**
     * Returns the original exception as thrown by the tool executor, before any
     * cause unwrapping. May be {@code null} if not provided.
     *
     * @since 1.17.0
     */
    public Exception originalException() {
        return originalException;
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
                && Objects.equals(invocationContext, that.invocationContext)
                && Objects.equals(originalException, that.originalException);
    }

    @Override
    public int hashCode() {
        return Objects.hash(toolExecutionRequest, invocationContext, originalException);
    }

    @Override
    public String toString() {
        return "ToolErrorContext{" +
                "toolExecutionRequest=" + toolExecutionRequest +
                ", invocationContext=" + invocationContext +
                ", originalException=" + originalException +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private ToolExecutionRequest toolExecutionRequest;
        private InvocationContext invocationContext;
        private Exception originalException;

        public Builder toolExecutionRequest(ToolExecutionRequest toolExecutionRequest) {
            this.toolExecutionRequest = toolExecutionRequest;
            return this;
        }

        public Builder invocationContext(InvocationContext invocationContext) {
            this.invocationContext = invocationContext;
            return this;
        }

        /**
         * Sets the original exception thrown during tool execution.
         *
         * @since 1.17.0
         */
        public Builder originalException(Exception originalException) {
            this.originalException = originalException;
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
