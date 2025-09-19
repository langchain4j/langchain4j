package dev.langchain4j.invocation;

import java.util.Objects;
import dev.langchain4j.model.chat.ChatModel;

/**
 * Represents the context of a single AI Service invocation.
 * A new instance is created each time an AI Service method is invoked,
 * and it exists until the end of the AI Service invocation,
 * potentially spanning multiple calls to the {@link ChatModel}.
 *
 * @since 1.6.0
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
