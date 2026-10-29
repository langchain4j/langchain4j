package dev.langchain4j.model.typesafe;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.decision.request.DecisionRequestParameters;
import dev.langchain4j.model.decision.request.DefaultDecisionRequestParameters;
import java.util.Map;
import java.util.Objects;

/**
 * The per-call parameters of a {@link TypeSafeDecisionModel} request. Parameters that are not set fall back to the ones
 * configured on the {@link TypeSafeDecisionModel} builder. Parameters made for another decision model are rejected with
 * an {@link IllegalArgumentException}.
 * <pre>{@code
 * DecisionRequest request = DecisionRequest.builder()
 *         .input("...")
 *         .question("spam", YesNoQuestion.from("Is this message spam?"))
 *         .parameters(TypeSafeDecisionRequestParameters.builder()
 *                 .customParameters(Map.of("parameter_name", "value"))
 *                 .build())
 *         .build();
 * }</pre>
 *
 * @since 1.23.0
 */
@Experimental
public class TypeSafeDecisionRequestParameters extends DefaultDecisionRequestParameters {

    private final Map<String, Object> customParameters;

    private TypeSafeDecisionRequestParameters(Builder builder) {
        super(builder);
        this.customParameters = copy(builder.customParameters);
    }

    /**
     * The custom parameters added to the body of the request, empty if not set.
     */
    public Map<String, Object> customParameters() {
        return customParameters;
    }

    @Override
    public TypeSafeDecisionRequestParameters overrideWith(DecisionRequestParameters that) {
        return builder().overrideWith(this).overrideWith(that).build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        TypeSafeDecisionRequestParameters that = (TypeSafeDecisionRequestParameters) o;
        return Objects.equals(customParameters, that.customParameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), customParameters);
    }

    @Override
    public String toString() {
        return "TypeSafeDecisionRequestParameters{modelName=" + modelName() + ", customParameters="
                + customParameters + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends DefaultDecisionRequestParameters.Builder<Builder> {

        private Map<String, Object> customParameters;

        @Override
        public Builder overrideWith(DecisionRequestParameters parameters) {
            if (parameters != null
                    && !(parameters instanceof TypeSafeDecisionRequestParameters)
                    && parameters.getClass() != DefaultDecisionRequestParameters.class) {
                throw new IllegalArgumentException("TypeSafeDecisionModel does not support "
                        + parameters.getClass().getSimpleName() + ": use TypeSafeDecisionRequestParameters, or "
                        + "DecisionRequestParameters.builder() for the parameters common to all decision models");
            }
            super.overrideWith(parameters);
            if (parameters instanceof TypeSafeDecisionRequestParameters typeSafeParameters) {
                customParameters(getOrDefault(typeSafeParameters.customParameters(), customParameters));
            }
            return this;
        }

        /**
         * Custom parameters added to the body of the request, for example to use a feature of the System One API that
         * {@link TypeSafeDecisionModel} does not support yet. A custom parameter with the same name as a parameter that
         * {@link TypeSafeDecisionModel} sends replaces it.
         * <p>
         * If not empty, they replace (are not merged with) the custom parameters set on the {@link
         * TypeSafeDecisionModel} builder for this request. If not set or empty, the custom parameters of the builder
         * are used.
         */
        public Builder customParameters(Map<String, Object> customParameters) {
            this.customParameters = customParameters;
            return this;
        }

        @Override
        public TypeSafeDecisionRequestParameters build() {
            return new TypeSafeDecisionRequestParameters(this);
        }
    }
}
