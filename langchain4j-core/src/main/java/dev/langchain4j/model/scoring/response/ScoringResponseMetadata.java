package dev.langchain4j.model.scoring.response;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.output.TokenUsage;
import java.util.Objects;

/**
 * The metadata of a {@link ScoringResponse}: the {@link #modelName()} that produced the scores and the
 * {@link #tokenUsage()} (or provider billing units) the call consumed. Provider integrations extend this class
 * (and its self-typed {@link Builder}) to add provider-specific metadata.
 *
 * @since 1.19.0
 */
@Experimental
public class ScoringResponseMetadata {

    private final String modelName;
    private final TokenUsage tokenUsage;

    protected ScoringResponseMetadata(Builder<?> builder) {
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
        ScoringResponseMetadata that = (ScoringResponseMetadata) o;
        return Objects.equals(modelName, that.modelName) && Objects.equals(tokenUsage, that.tokenUsage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(modelName, tokenUsage);
    }

    @Override
    public String toString() {
        return "ScoringResponseMetadata{modelName=" + modelName + ", tokenUsage=" + tokenUsage + '}';
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

        public ScoringResponseMetadata build() {
            return new ScoringResponseMetadata(this);
        }
    }
}
