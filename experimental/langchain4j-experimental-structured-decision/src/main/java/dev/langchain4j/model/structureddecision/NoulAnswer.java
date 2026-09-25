package dev.langchain4j.model.structureddecision;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureBetween;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;

import dev.langchain4j.Experimental;
import java.util.Map;

/** A probability-like System One noul value, independent of answer confidence. */
@Experimental
public record NoulAnswer(Double value, Double confidence, ConfidenceProvenance confidenceProvenance,
                         Map<String, Object> metadata) implements StructuredDecisionAnswer {

    public NoulAnswer {
        ensureNotNull(value, "value");
        ensureTrue(Double.isFinite(value), "value must be finite");
        value = ensureBetween(value, 0, 1, "value");
        AnswerValidation.validateConfidence(confidence, confidenceProvenance);
        metadata = DecisionSnapshots.map(ensureNotNull(metadata, "metadata"));
    }

    public NoulAnswer(Double value) {
        this(value, null, null, Map.of());
    }

    public Double noul() {
        return value;
    }
}
