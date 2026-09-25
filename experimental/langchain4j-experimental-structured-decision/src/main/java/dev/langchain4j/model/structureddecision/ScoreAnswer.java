package dev.langchain4j.model.structureddecision;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;

import dev.langchain4j.Experimental;
import java.util.Map;

/** A position on an ordered score scale. */
@Experimental
public record ScoreAnswer(
        Double value, Double confidence, ConfidenceProvenance confidenceProvenance, Map<String, Object> metadata)
        implements StructuredDecisionAnswer {

    public ScoreAnswer {
        ensureNotNull(value, "value");
        ensureTrue(Double.isFinite(value), "value must be finite");
        AnswerValidation.validateConfidence(confidence, confidenceProvenance);
        metadata = DecisionSnapshots.map(ensureNotNull(metadata, "metadata"));
    }

    public ScoreAnswer(Double value) {
        this(value, null, null, Map.of());
    }

    public Double score() {
        return value;
    }
}
