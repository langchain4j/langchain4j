package dev.langchain4j.model.decision;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Default implementation of {@link DecisionRequestParameters}. */
@Experimental
public class DefaultDecisionRequestParameters implements DecisionRequestParameters {

    private final String modelName;
    private final Map<String, Object> additionalProperties;

    protected DefaultDecisionRequestParameters(Builder<?> builder) {
        this.modelName = builder.modelName;
        this.additionalProperties = copy(builder.additionalProperties);
    }

    @Override
    public String modelName() {
        return modelName;
    }

    @Override
    public Map<String, Object> additionalProperties() {
        return additionalProperties;
    }

    @Override
    public DecisionRequestParameters overrideWith(DecisionRequestParameters that) {
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
        DefaultDecisionRequestParameters that = (DefaultDecisionRequestParameters) o;
        return Objects.equals(modelName, that.modelName)
                && Objects.equals(additionalProperties, that.additionalProperties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(modelName, additionalProperties);
    }

    @Override
    public String toString() {
        return "DefaultDecisionRequestParameters{modelName=" + modelName + ", additionalProperties="
                + additionalProperties + '}';
    }

    public static class Builder<B extends Builder<B>> {
        protected String modelName;
        protected final Map<String, Object> additionalProperties = new LinkedHashMap<>();

        public B modelName(String modelName) {
            this.modelName = modelName;
            return self();
        }

        public B additionalProperties(Map<String, Object> additionalProperties) {
            this.additionalProperties.clear();
            ensureNotNull(additionalProperties, "additionalProperties").forEach(this::additionalProperty);
            return self();
        }

        public B additionalProperty(String name, Object value) {
            this.additionalProperties.put(
                    ensureNotBlank(name, "additional property name"),
                    ensureNotNull(value, "additional property value"));
            return self();
        }

        public B overrideWith(DecisionRequestParameters parameters) {
            if (parameters != null) {
                this.modelName = getOrDefault(parameters.modelName(), this.modelName);
                this.additionalProperties.putAll(parameters.additionalProperties());
            }
            return self();
        }

        @SuppressWarnings("unchecked")
        protected B self() {
            return (B) this;
        }

        public DefaultDecisionRequestParameters build() {
            return new DefaultDecisionRequestParameters(this);
        }
    }
}
