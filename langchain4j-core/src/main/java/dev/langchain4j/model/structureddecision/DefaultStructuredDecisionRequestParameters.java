package dev.langchain4j.model.structureddecision;

import static dev.langchain4j.internal.Utils.getOrDefault;

import dev.langchain4j.Experimental;
import java.util.Objects;

/** Default implementation of {@link StructuredDecisionRequestParameters}. */
@Experimental
public class DefaultStructuredDecisionRequestParameters implements StructuredDecisionRequestParameters {

    private final String modelName;

    protected DefaultStructuredDecisionRequestParameters(Builder<?> builder) {
        this.modelName = builder.modelName;
    }

    @Override
    public String modelName() {
        return modelName;
    }

    @Override
    public StructuredDecisionRequestParameters overrideWith(StructuredDecisionRequestParameters that) {
        if (that == null) {
            return this;
        }
        return builder().overrideWith(this).overrideWith(that).build();
    }

    public static Builder<?> builder() {
        return new Builder<>();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DefaultStructuredDecisionRequestParameters that = (DefaultStructuredDecisionRequestParameters) o;
        return Objects.equals(modelName, that.modelName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(modelName);
    }

    @Override
    public String toString() {
        return "DefaultStructuredDecisionRequestParameters{modelName=" + modelName + '}';
    }

    public static class Builder<B extends Builder<B>> {
        protected String modelName;

        public B modelName(String modelName) {
            this.modelName = modelName;
            return self();
        }

        public B overrideWith(StructuredDecisionRequestParameters parameters) {
            if (parameters != null) {
                this.modelName = getOrDefault(parameters.modelName(), this.modelName);
            }
            return self();
        }

        @SuppressWarnings("unchecked")
        protected B self() {
            return (B) this;
        }

        public DefaultStructuredDecisionRequestParameters build() {
            return new DefaultStructuredDecisionRequestParameters(this);
        }
    }
}
