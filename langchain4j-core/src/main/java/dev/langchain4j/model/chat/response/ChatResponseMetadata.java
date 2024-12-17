package dev.langchain4j.model.chat.response;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;

import java.util.Objects;

@Experimental
public class ChatResponseMetadata {
    // TODO name: ChatMetadata?

    private final String id; // TODO responseId?
    private final String modelName; // TODO name
    private final TokenUsage tokenUsage;
    private final FinishReason finishReason;

    protected ChatResponseMetadata(Builder<?> builder) {
        this.id = builder.id;
        this.modelName = builder.modelName;
        this.tokenUsage = builder.tokenUsage;
        this.finishReason = builder.finishReason;
    }

    @Experimental
    public String id() { // TODO name
        return id;
    }

    @Experimental
    public String modelName() { // TODO name
        return modelName;
    }

    @Experimental
    public TokenUsage tokenUsage() {
        return tokenUsage;
    }

    @Experimental
    public FinishReason finishReason() {
        return finishReason;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChatResponseMetadata that = (ChatResponseMetadata) o;
        return Objects.equals(id, that.id)
                && Objects.equals(modelName, that.modelName)
                && Objects.equals(tokenUsage, that.tokenUsage)
                && Objects.equals(finishReason, that.finishReason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, modelName, tokenUsage, finishReason);
    }

    @Override
    public String toString() {
        return "ChatResponseMetadata{" +
                "id='" + id + '\'' +
                ", modelName='" + modelName + '\'' +
                ", tokenUsage=" + tokenUsage +
                ", finishReason=" + finishReason +
                '}';
    }

    public static Builder<?> builder() {
        return new Builder<>();
    }

    public static class Builder<T extends ChatResponseMetadata.Builder<T>> {

        private String id;
        private String modelName;
        private TokenUsage tokenUsage;
        private FinishReason finishReason;

        @Experimental
        public T id(String id) { // TODO names
            this.id = id;
            return (T) this;
        }

        @Experimental
        public T modelName(String modelName) { // TODO names
            this.modelName = modelName;
            return (T) this;
        }

        @Experimental
        public T tokenUsage(TokenUsage tokenUsage) {
            this.tokenUsage = tokenUsage;
            return (T) this;
        }

        @Experimental
        public T finishReason(FinishReason finishReason) {
            this.finishReason = finishReason;
            return (T) this;
        }

        public ChatResponseMetadata build() {
            return new ChatResponseMetadata(this);
        }
    }
}
