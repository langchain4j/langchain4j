package dev.langchain4j.model.scoring.request;

import static dev.langchain4j.internal.Utils.getOrDefault;

import dev.langchain4j.Experimental;
import java.util.Objects;

/**
 * The default implementation of {@link ScoringRequestParameters}. Provider integrations extend this class (and its
 * self-typed {@link Builder}) to add provider-specific parameters.
 *
 * @since 1.20.0
 */
@Experimental
public class DefaultScoringRequestParameters implements ScoringRequestParameters {

    private final String modelName;

    protected DefaultScoringRequestParameters(Builder<?> builder) {
        this.modelName = builder.modelName;
    }

    @Override
    public String modelName() {
        return modelName;
    }

    @Override
    public ScoringRequestParameters overrideWith(ScoringRequestParameters that) {
        if (that == null) {
            return this;
        }
        return builder().overrideWith(this).overrideWith(that).build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DefaultScoringRequestParameters that = (DefaultScoringRequestParameters) o;
        return Objects.equals(modelName, that.modelName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(modelName);
    }

    @Override
    public String toString() {
        return "DefaultScoringRequestParameters{modelName=" + modelName + '}';
    }

    public static Builder<?> builder() {
        return new Builder<>();
    }

    public static class Builder<B extends Builder<B>> {

        protected String modelName;

        public B modelName(String modelName) {
            this.modelName = modelName;
            return self();
        }

        /**
         * Copies the populated parameters from the given instance into this builder.
         *
         * @param parameters the parameters to copy, may be {@code null}.
         * @return {@code this}.
         */
        public B overrideWith(ScoringRequestParameters parameters) {
            if (parameters != null) {
                this.modelName = getOrDefault(parameters.modelName(), this.modelName);
            }
            return self();
        }

        @SuppressWarnings("unchecked")
        protected B self() {
            return (B) this;
        }

        public DefaultScoringRequestParameters build() {
            return new DefaultScoringRequestParameters(this);
        }
    }
}
