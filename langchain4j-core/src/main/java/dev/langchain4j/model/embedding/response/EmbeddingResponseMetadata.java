package dev.langchain4j.model.embedding.response;

import dev.langchain4j.Experimental;
import dev.langchain4j.internal.JacocoIgnoreCoverageGenerated;
import dev.langchain4j.model.output.TokenUsage;
import java.util.Objects;

/**
 * Common metadata returned alongside an {@link EmbeddingResponse}, mirroring
 * {@link dev.langchain4j.model.chat.response.ChatResponseMetadata} (minus chat-only fields such as
 * {@code finishReason}). Provider integrations may extend this class to add provider-specific metadata.
 * <p>
 * The {@link TokenUsage} type is deliberately reused from the chat side: embedding APIs report input token
 * counts the same way (with {@link TokenUsage#outputTokenCount()} simply left {@code null}), so the existing
 * summing utilities keep working across batched requests.
 *
 * @since 1.18.0
 */
@Experimental
@JacocoIgnoreCoverageGenerated
public class EmbeddingResponseMetadata {

    private final String modelName; // TODO needed?
    private final TokenUsage tokenUsage;

    protected EmbeddingResponseMetadata(Builder<?> builder) {
        this.modelName = builder.modelName;
        this.tokenUsage = builder.tokenUsage;
    }

    public String modelName() {
        return modelName;
    }

    public TokenUsage tokenUsage() {
        return tokenUsage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmbeddingResponseMetadata that = (EmbeddingResponseMetadata) o;
        return Objects.equals(modelName, that.modelName) && Objects.equals(tokenUsage, that.tokenUsage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(modelName, tokenUsage);
    }

    @Override
    public String toString() {
        return "EmbeddingResponseMetadata{modelName='" + modelName + '\'' + ", tokenUsage=" + tokenUsage + '}';
    }

    public static Builder<?> builder() {
        return new Builder<>();
    }

    public static class Builder<T extends Builder<T>> {

        private String modelName;
        private TokenUsage tokenUsage;

        public T modelName(String modelName) {
            this.modelName = modelName;
            return self();
        }

        public T tokenUsage(TokenUsage tokenUsage) {
            this.tokenUsage = tokenUsage;
            return self();
        }

        @SuppressWarnings("unchecked")
        protected T self() {
            return (T) this;
        }

        public EmbeddingResponseMetadata build() {
            return new EmbeddingResponseMetadata(this);
        }
    }
}
