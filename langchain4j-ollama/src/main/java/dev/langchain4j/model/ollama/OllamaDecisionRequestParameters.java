package dev.langchain4j.model.ollama;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.decision.request.DecisionRequestParameters;
import dev.langchain4j.model.decision.request.DefaultDecisionRequestParameters;
import java.util.Map;
import java.util.Objects;

/**
 * The per-call parameters of an {@link OllamaDecisionModel} request. Parameters that are not set fall back to the ones
 * configured on the {@link OllamaDecisionModel} builder. Parameters made for another decision model are rejected with
 * an {@link IllegalArgumentException}.
 * <pre>{@code
 * DecisionRequest request = DecisionRequest.builder()
 *         .input("...")
 *         .question("spam", YesNoQuestion.from("Is this message spam?"))
 *         .parameters(OllamaDecisionRequestParameters.builder()
 *                 .customParameters(Map.of("parameter_name", "value"))
 *                 .build())
 *         .build();
 * }</pre>
 *
 * @since 1.23.0
 */
@Experimental
public class OllamaDecisionRequestParameters extends DefaultDecisionRequestParameters {

    private final Map<String, Object> customParameters;

    private OllamaDecisionRequestParameters(Builder builder) {
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
    public OllamaDecisionRequestParameters overrideWith(DecisionRequestParameters that) {
        return builder().overrideWith(this).overrideWith(that).build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        OllamaDecisionRequestParameters that = (OllamaDecisionRequestParameters) o;
        return Objects.equals(customParameters, that.customParameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), customParameters);
    }

    @Override
    public String toString() {
        return "OllamaDecisionRequestParameters{modelName=" + modelName() + ", customParameters="
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
                    && !(parameters instanceof OllamaDecisionRequestParameters)
                    && parameters.getClass() != DefaultDecisionRequestParameters.class) {
                throw new IllegalArgumentException("OllamaDecisionModel does not support "
                        + parameters.getClass().getSimpleName() + ": use OllamaDecisionRequestParameters, or "
                        + "DecisionRequestParameters.builder() for the parameters common to all decision models");
            }
            super.overrideWith(parameters);
            if (parameters instanceof OllamaDecisionRequestParameters ollamaParameters) {
                customParameters(getOrDefault(ollamaParameters.customParameters(), customParameters));
            }
            return this;
        }

        /**
         * Custom parameters added to the body of the request, for example to use a feature of the Ollama System One API
         * that {@link OllamaDecisionModel} does not support yet. A custom parameter with the same name as a parameter
         * that {@link OllamaDecisionModel} sends replaces it.
         * <p>
         * If not empty, they replace (are not merged with) the custom parameters set on the {@link OllamaDecisionModel}
         * builder for this request. If not set or empty, the custom parameters of the builder are used.
         */
        public Builder customParameters(Map<String, Object> customParameters) {
            this.customParameters = customParameters;
            return this;
        }

        @Override
        public OllamaDecisionRequestParameters build() {
            return new OllamaDecisionRequestParameters(this);
        }
    }
}
