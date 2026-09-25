package dev.langchain4j.model.decision;

import static dev.langchain4j.internal.ValidationUtils.ensureBetween;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;

final class AnswerValidation {

    private AnswerValidation() {}

    static void validateConfidence(Double confidence, ConfidenceProvenance provenance) {
        ensureTrue(
                (confidence == null) == (provenance == null),
                "confidence and confidenceProvenance must either both be present or both be absent");
        if (confidence != null) {
            ensureTrue(Double.isFinite(confidence), "confidence must be finite");
            ensureBetween(confidence, 0, 1, "confidence");
        }
    }
}
