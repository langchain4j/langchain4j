package dev.langchain4j.service.decision;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The answer to a question of a decision service that chooses one of several options: the chosen option, the
 * probability of each option and, if the model reports one, a confidence.
 *
 * Decision services currently create it for enums only: the enum constants are the options.
 *
 * @param <E> the type of the options, for example an enum whose constants are the options
 * @since 1.21.0
 */
@Experimental
public final class Choice<E> {

    private final E value;
    private final Map<E, Double> probabilities;
    private final Double confidence;

    private Choice(Builder<E> builder) {
        this.value = ensureNotNull(builder.value, "value");
        Map<E, Double> probabilities = new LinkedHashMap<>();
        if (builder.probabilities != null) {
            builder.probabilities.forEach((option, probability) -> probabilities.put(
                    ensureNotNull(option, "option"),
                    ensureProbability(ensureNotNull(probability, "probability"), "probability")));
        }
        this.probabilities = Collections.unmodifiableMap(probabilities);
        this.confidence =
                builder.confidence == null ? null : ensureProbability(builder.confidence, "confidence");
    }

    private static double ensureProbability(double value, String name) {
        if (!(value >= 0 && value <= 1)) {
            throw new IllegalArgumentException(name + " must be between 0 and 1, but was " + value);
        }
        return value;
    }

    public static <E> Builder<E> builder() {
        return new Builder<>();
    }

    /**
     * The chosen option, usually the one with the highest probability.
     */
    public E value() {
        return value;
    }

    /**
     * The probability of each option. Empty if the model does not report probabilities.
     */
    public Map<E, Double> probabilities() {
        return probabilities;
    }

    /**
     * The probability of the given option, or 0 if the model reported probabilities but none for this option.
     *
     * @throws IllegalStateException if the model did not report probabilities.
     */
    public double probability(E option) {
        ensureProbabilities();
        return probabilities.getOrDefault(option, 0.0);
    }

    /**
     * The difference between the two highest probabilities, from 0 to 1. A small margin means the model hesitated
     * between two options, which is a common signal to escalate, for example to a human.
     * <p>
     * If the model reported probabilities for only some of the options, the probability it did not report is
     * assumed to belong to a single other option, so the margin is never overestimated.
     *
     * @throws IllegalStateException if the model did not report probabilities.
     */
    public double margin() {
        ensureProbabilities();
        List<Double> sorted = probabilities.values().stream()
                .sorted(Comparator.reverseOrder())
                .toList();
        double unreported = 1 - sorted.stream().mapToDouble(Double::doubleValue).sum();
        double second = Math.max(sorted.size() < 2 ? 0 : sorted.get(1), unreported);
        return Math.max(0, sorted.get(0) - second);
    }

    private void ensureProbabilities() {
        if (probabilities.isEmpty()) {
            throw new IllegalStateException("The model did not report probabilities");
        }
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

    public static final class Builder<E> {

        private E value;
        private Map<E, Double> probabilities;
        private Double confidence;

        public Builder<E> value(E value) {
            this.value = value;
            return this;
        }

        public Builder<E> probabilities(Map<E, Double> probabilities) {
            this.probabilities = probabilities;
            return this;
        }

        public Builder<E> confidence(Double confidence) {
            this.confidence = confidence;
            return this;
        }

        public Choice<E> build() {
            return new Choice<>(this);
        }
    }
}
