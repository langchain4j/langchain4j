package dev.langchain4j.service.tool;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import java.util.Objects;

/**
 * @since 1.4.0
 */
public class ToolErrorContext {

    private final ToolExecutionRequest toolExecutionRequest;
    private final InvocationContext invocationContext;
    private final Exception rawError;

    public ToolErrorContext(Builder builder) {
        this.toolExecutionRequest = ensureNotNull(builder.toolExecutionRequest, "toolExecutionRequest");
        this.invocationContext = ensureNotNull(builder.invocationContext, "invocationContext");
        this.rawError = builder.rawError;
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
     * Returns the raw error as thrown by the tool executor, before any
     * cause unwrapping. May be {@code null} if not provided.
     *
     * @since 1.17.0
     */
    public Exception rawError() {
        return rawError;
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
                && Objects.equals(rawError, that.rawError);
    }

    @Override
    public int hashCode() {
        return Objects.hash(toolExecutionRequest, invocationContext, rawError);
    }

    @Override
    public String toString() {
        return "ToolErrorContext{" + "toolExecutionRequest="
                + toolExecutionRequest + ", invocationContext="
                + invocationContext + ", rawError="
                + rawError + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private ToolExecutionRequest toolExecutionRequest;
        private InvocationContext invocationContext;
        private Exception rawError;

        public Builder toolExecutionRequest(ToolExecutionRequest toolExecutionRequest) {
            this.toolExecutionRequest = toolExecutionRequest;
            return this;
        }

        public Builder invocationContext(InvocationContext invocationContext) {
            this.invocationContext = invocationContext;
            return this;
        }

        /**
         * Sets the raw error thrown during tool execution.
         *
         * @since 1.17.0
         */
        public Builder rawError(Exception rawError) {
            this.rawError = rawError;
            return this;
        }

        /**
         * @deprecated Please set {@link #invocationContext(InvocationContext)} instead
         */
        @Deprecated(since = "1.5.0")
        public Builder memoryId(Object memoryId) {
            this.invocationContext =
                    InvocationContext.builder().chatMemoryId(memoryId).build();
            return this;
        }

        public ToolErrorContext build() {
            return new ToolErrorContext(this);
        }
    }
}
