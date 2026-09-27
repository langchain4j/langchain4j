package dev.langchain4j.model.decision.response;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;

import dev.langchain4j.Experimental;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The answer to a {@link dev.langchain4j.model.decision.request.ScaleQuestion}: a value, the probability of each
 * level and, if the model reports one, a confidence.
 *
 * @since 1.21.0
 */
@Experimental
public final class ScaleAnswer implements DecisionAnswer {

    private final double value;
    private final List<Double> probabilities;
    private final Double confidence;

    private ScaleAnswer(Builder builder) {
        ensureNotNull(builder.value, "value");
        ensureTrue(Double.isFinite(builder.value), "value must be a finite number, but was " + builder.value);
        this.value = builder.value;
        this.probabilities = copy(builder.probabilities);
        this.confidence = Probabilities.ensureNullableProbability(builder.confidence, "confidence");
    }

    /**
     * The probability-weighted mean of the level indexes, from 0 (the lowest level) to {@code n - 1} (the highest
     * level). It can fall between two levels: with levels "Calm", "Frustrated" and "Angry", a value of 1.4 means
     * "between frustrated and angry, closer to frustrated".
     */
    public double value() {
        return value;
    }

    /**
     * The probability of each level, indexed like
     * {@link dev.langchain4j.model.decision.request.ScaleQuestion#levels()}. Empty if the model does not report
     * probabilities.
     */
    public List<Double> probabilities() {
        return probabilities;
    }

    /**
     * How confident the model is in {@link #value()}, from 0 to 1, or {@code null} if the model does not report a
     * confidence.
     * <p>
     * The formula is defined by each model and differs between models, so a threshold tuned for one model does not
     * carry over to another.
     */
    public Double confidence() {
        return confidence;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ScaleAnswer that)) return false;
        return Double.compare(value, that.value) == 0
                && Objects.equals(probabilities, that.probabilities)
                && Objects.equals(confidence, that.confidence);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, probabilities, confidence);
    }

    @Override
    public String toString() {
        return "ScaleAnswer{value=" + value + ", probabilities=" + probabilities + ", confidence=" + confidence + '}';
    }

    public static final class Builder {

        private Double value;
        private final List<Double> probabilities = new ArrayList<>();
        private Double confidence;

        public Builder value(Double value) {
            this.value = value;
            return this;
        }

        /**
         * Sets the probability of each level, ordered from the lowest level to the highest.
         */
        public Builder probabilities(List<Double> probabilities) {
            this.probabilities.clear();
            if (probabilities != null) {
                probabilities.forEach(
                        probability -> this.probabilities.add(Probabilities.ensureProbability(probability, "probability")));
            }
            return this;
        }

        public Builder confidence(Double confidence) {
            this.confidence = confidence;
            return this;
        }

        public ScaleAnswer build() {
            return new ScaleAnswer(this);
        }
    }
}
