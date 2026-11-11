package dev.langchain4j.model.decision.response;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;

final class Probabilities {

    private Probabilities() {}

    static double ensureProbability(Double value, String name) {
        ensureNotNull(value, name);
        ensureTrue(value >= 0 && value <= 1, name + " must be between 0 and 1, but was " + value);
        return value;
    }

    static Double ensureNullableProbability(Double value, String name) {
        return value == null ? null : ensureProbability(value, name);
    }
}
