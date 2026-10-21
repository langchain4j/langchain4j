package dev.langchain4j.model.decision.response;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.output.TokenUsage;
import java.util.Objects;

/**
 * The metadata of a {@link DecisionResponse}: the {@link #modelName()} that produced the answers and the
 * {@link #tokenUsage()} the call consumed. Provider integrations extend this class (and its self-typed
 * {@link Builder}) to add provider-specific metadata.
 *
 * @since 1.21.0
 */
@Experimental
public class DecisionResponseMetadata {

    private final String modelName;
    private final TokenUsage tokenUsage;

    protected DecisionResponseMetadata(Builder<?> builder) {
        this.modelName = builder.modelName;
        this.tokenUsage = builder.tokenUsage;
    }

    /**
     * The name of the model that produced the answers, as reported by the provider. This can be more specific than
     * the requested name, for example a pinned version when an alias such as "latest" was requested.
     */
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
        DecisionResponseMetadata that = (DecisionResponseMetadata) o;
        return Objects.equals(modelName, that.modelName) && Objects.equals(tokenUsage, that.tokenUsage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(modelName, tokenUsage);
    }

    @Override
    public String toString() {
        return "DecisionResponseMetadata{modelName=" + modelName + ", tokenUsage=" + tokenUsage + '}';
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

        public DecisionResponseMetadata build() {
            return new DecisionResponseMetadata(this);
        }
    }
}
