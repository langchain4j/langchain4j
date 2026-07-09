package dev.langchain4j.model.embedding.response;

import dev.langchain4j.Experimental;
import dev.langchain4j.internal.JacocoIgnoreCoverageGenerated;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.util.Objects;

/**
 * Metadata returned alongside an {@link EmbeddingResponse}, describing the call that produced it: the
 * {@link #modelName() model name} and the {@link #tokenUsage() token usage}. Provider integrations may extend
 * this class to add provider-specific metadata.
 * <p>
 * Embedding APIs report only input token counts, so {@link TokenUsage#outputTokenCount()} is left {@code null}.
 * {@link #finishReason()} is not meaningful for most embedding models and is usually {@code null}.
 *
 * @since 1.18.0
 */
@Experimental
@JacocoIgnoreCoverageGenerated
public class EmbeddingResponseMetadata {

    private final String modelName; // TODO needed?
    private final TokenUsage tokenUsage;
    private final FinishReason finishReason;

    protected EmbeddingResponseMetadata(Builder<?> builder) {
        this.modelName = builder.modelName;
        this.tokenUsage = builder.tokenUsage;
        this.finishReason = builder.finishReason;
    }

    public String modelName() {
        return modelName;
    }

    public TokenUsage tokenUsage() {
        return tokenUsage;
    }

    public FinishReason finishReason() {
        return finishReason;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmbeddingResponseMetadata that = (EmbeddingResponseMetadata) o;
        return Objects.equals(modelName, that.modelName)
                && Objects.equals(tokenUsage, that.tokenUsage)
                && Objects.equals(finishReason, that.finishReason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(modelName, tokenUsage, finishReason);
    }

    @Override
    public String toString() {
        return "EmbeddingResponseMetadata{modelName='" + modelName + '\'' + ", tokenUsage=" + tokenUsage
                + ", finishReason=" + finishReason + '}';
    }

    public static Builder<?> builder() {
        return new Builder<>();
    }

    public static class Builder<T extends Builder<T>> {

        private String modelName;
        private TokenUsage tokenUsage;
        private FinishReason finishReason;

        public T modelName(String modelName) {
            this.modelName = modelName;
            return self();
        }

        public T tokenUsage(TokenUsage tokenUsage) {
            this.tokenUsage = tokenUsage;
            return self();
        }

        public T finishReason(FinishReason finishReason) {
            this.finishReason = finishReason;
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
