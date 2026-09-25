package dev.langchain4j.model.structureddecision;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import java.util.Map;

/** A selected option ID. */
@Experimental
public record ChoiceAnswer(String value, Double confidence, ConfidenceProvenance confidenceProvenance,
                           Map<String, Object> metadata) implements StructuredDecisionAnswer {

    public ChoiceAnswer {
        value = ensureNotBlank(value, "value");
        AnswerValidation.validateConfidence(confidence, confidenceProvenance);
        metadata = DecisionSnapshots.map(ensureNotNull(metadata, "metadata"));
    }

    public ChoiceAnswer(String value) {
        this(value, null, null, Map.of());
    }

    public String choice() {
        return value;
    }
}
