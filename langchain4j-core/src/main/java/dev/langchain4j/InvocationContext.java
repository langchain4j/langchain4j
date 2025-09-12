package dev.langchain4j;

import java.util.Objects;

/**
 * TODO
 *
 * @since 1.5.0
 */
public class InvocationContext { // TODO name (AiServiceInvocationContext?), module, package

    private final Object chatMemoryId;
    private final ExtraParameters extraParameters;

    public InvocationContext(Builder builder) {
        this.chatMemoryId = builder.chatMemoryId;
        this.extraParameters = builder.extraParameters;
    }

    public Object chatMemoryId() {
        return chatMemoryId;
    }

    public ExtraParameters extraParameters() {
        return extraParameters;
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        InvocationContext that = (InvocationContext) object;
        return Objects.equals(chatMemoryId, that.chatMemoryId)
                && Objects.equals(extraParameters, that.extraParameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chatMemoryId, extraParameters);
    }

    @Override
    public String toString() {
        return "InvocationContext{" +
                "chatMemoryId=" + chatMemoryId +
                ", extraParameters=" + extraParameters +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Object chatMemoryId;
        private ExtraParameters extraParameters;

        public Builder chatMemoryId(Object chatMemoryId) {
            this.chatMemoryId = chatMemoryId;
            return this;
        }

        public Builder extraParameters(ExtraParameters extraParameters) {
            this.extraParameters = extraParameters;
            return this;
        }

        public InvocationContext build() {
            return new InvocationContext(this);
        }
    }
}
