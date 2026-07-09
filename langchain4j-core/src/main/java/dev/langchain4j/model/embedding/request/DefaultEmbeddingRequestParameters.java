package dev.langchain4j.model.embedding.request;

import dev.langchain4j.Experimental;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The default, map-backed implementation of {@link EmbeddingRequestParameters}.
 * <p>
 * All values are stored in a single {@code Map<EmbeddingParameter<?>, Object>}, keyed by their
 * {@link EmbeddingParameter} token. As a result {@link #presentParameters()} is simply the map's key set and
 * can never drift from the typed getters: introducing a new parameter automatically makes it part of the
 * populated set, so an implementation that has not opted into it will reject requests that use it.
 * <p>
 * Only non-null values are stored, so a parameter is "present" exactly when it has been explicitly set.
 * Provider integrations extend this class (and its {@link Builder}) to add provider-specific parameters,
 * reusing the same map so the opt-in mechanism keeps working for them too.
 *
 * @since 1.18.0
 */
@Experimental
public class DefaultEmbeddingRequestParameters implements EmbeddingRequestParameters {

    private final Map<EmbeddingParameter<?>, Object> values;

    protected DefaultEmbeddingRequestParameters(Builder<?> builder) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(builder.values));
    }

    @Override
    public String modelName() {
        return parameter(MODEL_NAME);
    }

    @Override
    public Integer dimensions() {
        return parameter(DIMENSIONS);
    }

    @Override
    public EmbeddingInputType inputType() {
        return parameter(INPUT_TYPE);
    }

    @Override
    public <T> T parameter(EmbeddingParameter<T> parameter) {
        return parameter.cast(values.get(parameter));
    }

    @Override
    public Set<EmbeddingParameter<?>> presentParameters() {
        return values.keySet();
    }

    @Override
    public EmbeddingRequestParameters overrideWith(EmbeddingRequestParameters that) {
        // TODO what if that is more specific type like OpenAi...
        if (that == null || that.presentParameters().isEmpty()) {
            return this;
        }
        Builder<?> builder = new Builder<>();
        builder.values.putAll(this.values);
        for (EmbeddingParameter<?> parameter : that.presentParameters()) {
            builder.values.put(parameter, that.parameter(parameter));
        }
        return builder.build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DefaultEmbeddingRequestParameters that = (DefaultEmbeddingRequestParameters) o;
        return Objects.equals(values, that.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(values);
    }

    @Override
    public String toString() {
        return "DefaultEmbeddingRequestParameters{values=" + values + '}';
    }

    public static Builder<?> builder() {
        return new Builder<>();
    }

    public static class Builder<B extends Builder<B>> {

        protected final Map<EmbeddingParameter<?>, Object> values = new LinkedHashMap<>();

        /**
         * Sets a single parameter. A {@code null} value clears the parameter, keeping it out of the
         * {@link #presentParameters() populated set}.
         *
         * @param parameter the parameter token.
         * @param value     the value, or {@code null} to leave it unset.
         * @param <T>       the value type.
         * @return {@code this}.
         */
        public <T> B set(EmbeddingParameter<T> parameter, T value) {
            if (value == null) {
                values.remove(parameter);
            } else {
                values.put(parameter, value);
            }
            return self();
        }

        public B modelName(String modelName) {
            return set(EmbeddingRequestParameters.MODEL_NAME, modelName);
        }

        public B dimensions(Integer dimensions) {
            return set(EmbeddingRequestParameters.DIMENSIONS, dimensions);
        }

        public B inputType(EmbeddingInputType inputType) {
            return set(EmbeddingRequestParameters.INPUT_TYPE, inputType);
        }

        /**
         * Copies all populated parameters from the given instance into this builder.
         *
         * @param parameters the parameters to copy, may be {@code null}.
         * @return {@code this}.
         */
        public B overrideWith(EmbeddingRequestParameters parameters) {
            if (parameters != null) {
                for (EmbeddingParameter<?> parameter : parameters.presentParameters()) {
                    values.put(parameter, parameters.parameter(parameter));
                }
            }
            return self();
        }

        @SuppressWarnings("unchecked")
        protected B self() {
            return (B) this;
        }

        public DefaultEmbeddingRequestParameters build() {
            return new DefaultEmbeddingRequestParameters(this);
        }
    }
}
