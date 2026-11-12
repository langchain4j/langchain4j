package dev.langchain4j.model.googleai;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;

import dev.langchain4j.Experimental;
import dev.langchain4j.exception.InvalidDecisionResponseException;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.DecisionRequestParameters;
import dev.langchain4j.model.decision.request.DefaultDecisionRequestParameters;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The per-call parameters of a {@link GoogleAiGeminiDecisionModel} request. Parameters that are not set fall back to
 * the ones configured on the {@link GoogleAiGeminiDecisionModel} builder. Parameters made for another decision model
 * are rejected with an {@link IllegalArgumentException}.
 * <pre>{@code
 * DecisionRequest request = DecisionRequest.builder()
 *         .input("...")
 *         .question("spam", YesNoQuestion.from("Is this message spam?"))
 *         .parameters(GoogleAiGeminiDecisionRequestParameters.builder()
 *                 .modelName("gemini-decision-1")
 *                 .customParameters(Map.of("parameter_name", "value"))
 *                 .build())
 *         .build();
 * }</pre>
 *
 * @since 1.24.0
 */
@Experimental
public class GoogleAiGeminiDecisionRequestParameters extends DefaultDecisionRequestParameters {

    private final Map<String, Object> customParameters;

    private GoogleAiGeminiDecisionRequestParameters(Builder builder) {
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
    public GoogleAiGeminiDecisionRequestParameters overrideWith(DecisionRequestParameters that) {
        return builder().overrideWith(this).overrideWith(that).build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        GoogleAiGeminiDecisionRequestParameters that = (GoogleAiGeminiDecisionRequestParameters) o;
        return Objects.equals(customParameters, that.customParameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), customParameters);
    }

    @Override
    public String toString() {
        return "GoogleAiGeminiDecisionRequestParameters{modelName=" + modelName() + ", customParameters="
                + customParameters + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends DefaultDecisionRequestParameters.Builder<Builder> {

        private @Nullable Map<String, Object> customParameters;

        @Override
        public Builder overrideWith(@Nullable DecisionRequestParameters parameters) {
            if (parameters != null
                    && !(parameters instanceof GoogleAiGeminiDecisionRequestParameters)
                    && parameters.getClass() != DefaultDecisionRequestParameters.class) {
                throw new IllegalArgumentException("GoogleAiGeminiDecisionModel does not support "
                        + parameters.getClass().getSimpleName() + ": use GoogleAiGeminiDecisionRequestParameters, or "
                        + "DecisionRequestParameters.builder() for the parameters common to all decision models");
            }
            super.overrideWith(parameters);
            if (parameters instanceof GoogleAiGeminiDecisionRequestParameters geminiParameters) {
                customParameters(getOrDefault(geminiParameters.customParameters(), customParameters));
            }
            return this;
        }

        /**
         * Custom parameters added to the body of the request, for example to use a feature of the Gemini Decisions
         * API that {@link GoogleAiGeminiDecisionModel} does not support yet. A custom parameter with the same name as
         * a parameter that {@link GoogleAiGeminiDecisionModel} sends ({@code model}, {@code input} or
         * {@code questions}) replaces it. Do not replace {@code questions}: the answers are read against the
         * questions of the {@link DecisionRequest}, so the response would be rejected with an
         * {@link InvalidDecisionResponseException}.
         * <p>
         * If not empty, they replace (are not merged with) the custom parameters set on the
         * {@link GoogleAiGeminiDecisionModel} builder for this request. If not set ({@code null}) or empty, the custom
         * parameters of the builder are used.
         */
        public Builder customParameters(@Nullable Map<String, Object> customParameters) {
            this.customParameters = customParameters;
            return this;
        }

        @Override
        public GoogleAiGeminiDecisionRequestParameters build() {
            return new GoogleAiGeminiDecisionRequestParameters(this);
        }
    }
}
