package dev.langchain4j.model.google.genai;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.decision.request.DecisionRequestParameters;
import dev.langchain4j.model.decision.request.DefaultDecisionRequestParameters;

/**
 * The per-call parameters of a {@link GoogleGenAiDecisionModel} request. Parameters that are not set fall back to
 * the ones configured on the {@link GoogleGenAiDecisionModel} builder. Parameters made for another decision model
 * are rejected with an {@link IllegalArgumentException}.
 * <pre>{@code
 * DecisionRequest request = DecisionRequest.builder()
 *         .input("...")
 *         .question("spam", YesNoQuestion.from("Is this message spam?"))
 *         .parameters(GoogleGenAiDecisionRequestParameters.builder()
 *                 .modelName("gemini-decision-1")
 *                 .build())
 *         .build();
 * }</pre>
 *
 * @since 1.24.0
 */
@Experimental
public class GoogleGenAiDecisionRequestParameters extends DefaultDecisionRequestParameters {

    private GoogleGenAiDecisionRequestParameters(Builder builder) {
        super(builder);
    }

    @Override
    public GoogleGenAiDecisionRequestParameters overrideWith(DecisionRequestParameters that) {
        return builder().overrideWith(this).overrideWith(that).build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public String toString() {
        return "GoogleGenAiDecisionRequestParameters{modelName=" + modelName() + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends DefaultDecisionRequestParameters.Builder<Builder> {

        @Override
        public Builder overrideWith(DecisionRequestParameters parameters) {
            if (parameters != null
                    && !(parameters instanceof GoogleGenAiDecisionRequestParameters)
                    && parameters.getClass() != DefaultDecisionRequestParameters.class) {
                throw new IllegalArgumentException("GoogleGenAiDecisionModel does not support "
                        + parameters.getClass().getSimpleName() + ": use GoogleGenAiDecisionRequestParameters, or "
                        + "DecisionRequestParameters.builder() for the parameters common to all decision models");
            }
            super.overrideWith(parameters);
            return this;
        }

        @Override
        public GoogleGenAiDecisionRequestParameters build() {
            return new GoogleGenAiDecisionRequestParameters(this);
        }
    }
}
