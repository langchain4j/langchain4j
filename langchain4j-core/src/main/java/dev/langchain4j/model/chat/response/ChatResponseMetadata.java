package dev.langchain4j.model.chat.response;

import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;

public class ChatResponseMetadata {
    // TODO name: ChatMetadata?
    // TODO place

    private final String id;
    private final String modelName; // TODO name
    private final TokenUsage tokenUsage;
    private final FinishReason finishReason;

    protected ChatResponseMetadata(Builder builder) { // TODO
        this.id = builder.id;
        this.modelName = builder.modelName;
        this.tokenUsage = builder.tokenUsage;
        this.finishReason = builder.finishReason;
    }

    public String id() {
        return id;
    }

    public String modelName() { // TODO name
        return modelName;
    }

    public TokenUsage tokenUsage() {
        return tokenUsage;
    }

    public FinishReason finishReason() {
        return finishReason;
    }

    // TODO eq,hash,tostr, etc

    public static Builder builder() { // TODO
        return new Builder();
    }

    public static class Builder<T extends ChatResponseMetadata.Builder<T>> {

        private String id;
        private String modelName;
        private TokenUsage tokenUsage;
        private FinishReason finishReason;

        public T id(String id) {
            this.id = id;
            return (T) this;
        }

        public T modelName(String modelName) { // TODO name
            this.modelName = modelName;
            return (T) this;
        }

        public T tokenUsage(TokenUsage tokenUsage) {
            this.tokenUsage = tokenUsage;
            return (T) this;
        }

        public T finishReason(FinishReason finishReason) {
            this.finishReason = finishReason;
            return (T) this;
        }

        public ChatResponseMetadata build() {
            return new ChatResponseMetadata(this);
        }
    }
}
