package dev.langchain4j.service.decision;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * The answer to a question of a decision service whose options are the constants of an enum: the chosen constant,
 * the probability of each constant and, if the model reports one, a confidence.
 *
 * @param <E> the enum whose constants are the options
 * @since 1.21.0
 */
@Experimental
public final class Choice<E extends Enum<E>> {

    private final E value;
    private final Map<E, Double> probabilities;
    private final Double confidence;

    public Choice(E value, Map<E, Double> probabilities, Double confidence) {
        this.value = ensureNotNull(value, "value");
        this.probabilities = probabilities == null || probabilities.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new EnumMap<>(probabilities));
        this.confidence = confidence;
    }

    /**
     * The chosen constant, usually the one with the highest probability.
     */
    public E value() {
        return value;
    }

    /**
     * The probability of each constant. Empty if the model does not report probabilities.
     */
    public Map<E, Double> probabilities() {
        return probabilities;
    }

    /**
     * How confident the model is in {@link #value()}, from 0 to 1, or {@code null} if the model does not report a
     * confidence. The formula is defined by each model, so prefer {@link #probabilities()} for thresholds.
     */
    public Double confidence() {
        return confidence;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Choice<?> that)) return false;
        return Objects.equals(value, that.value)
                && Objects.equals(probabilities, that.probabilities)
                && Objects.equals(confidence, that.confidence);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, probabilities, confidence);
    }

    @Override
    public String toString() {
        return "Choice{value=" + value + ", probabilities=" + probabilities + ", confidence=" + confidence + '}';
    }
}
