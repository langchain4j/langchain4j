package dev.langchain4j.invocation;

import java.util.Objects;

/**
 * TODO
 * TODO scope
 *
 * @since 1.5.0
 */
public class InvocationContext {

    private final Object chatMemoryId;
    private final InvocationParameters invocationParameters;

    public InvocationContext(Builder builder) {
        this.chatMemoryId = builder.chatMemoryId;
        this.invocationParameters = builder.invocationParameters;
    }

    public Object chatMemoryId() {
        return chatMemoryId;
    }

    public InvocationParameters invocationParameters() {
        return invocationParameters;
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        InvocationContext that = (InvocationContext) object;
        return Objects.equals(chatMemoryId, that.chatMemoryId)
                && Objects.equals(invocationParameters, that.invocationParameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chatMemoryId, invocationParameters);
    }

    @Override
    public String toString() {
        return "InvocationContext{" +
                "chatMemoryId=" + chatMemoryId +
                ", invocationParameters=" + invocationParameters +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Object chatMemoryId;
        private InvocationParameters invocationParameters;

        public Builder chatMemoryId(Object chatMemoryId) {
            this.chatMemoryId = chatMemoryId;
            return this;
        }

        public Builder invocationParameters(InvocationParameters invocationParameters) {
            this.invocationParameters = invocationParameters;
            return this;
        }

        public InvocationContext build() {
            return new InvocationContext(this);
        }
    }
}
