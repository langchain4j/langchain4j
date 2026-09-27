package dev.langchain4j.model.decision.response;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;

import dev.langchain4j.Experimental;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The answer to a {@link dev.langchain4j.model.decision.request.ScoreQuestion}: a score, the probability of each
 * level and, if the model reports one, a confidence.
 *
 * @since 1.21.0
 */
@Experimental
public final class ScoreAnswer implements DecisionAnswer {

    private final double score;
    private final List<Double> probabilities;
    private final Double confidence;

    private ScoreAnswer(Builder builder) {
        ensureNotNull(builder.score, "score");
        ensureTrue(Double.isFinite(builder.score), "score must be a finite number, but was " + builder.score);
        this.score = builder.score;
        this.probabilities = copy(builder.probabilities);
        this.confidence = Probabilities.ensureNullableProbability(builder.confidence, "confidence");
    }

    /**
     * The probability-weighted mean of the level indexes, from 0 (the lowest level) to {@code n - 1} (the highest
     * level). It can fall between two levels: with levels "Calm", "Frustrated" and "Angry", a score of 1.4 means
     * "between frustrated and angry, closer to frustrated".
     */
    public double score() {
        return score;
    }

    /**
     * The probability of each level, indexed like
     * {@link dev.langchain4j.model.decision.request.ScoreQuestion#levels()}. Empty if the model does not report
     * probabilities.
     */
    public List<Double> probabilities() {
        return probabilities;
    }

    /**
     * How confident the model is in {@link #score()}, from 0 to 1, or {@code null} if the model does not report a
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
        if (!(o instanceof ScoreAnswer that)) return false;
        return Double.compare(score, that.score) == 0
                && Objects.equals(probabilities, that.probabilities)
                && Objects.equals(confidence, that.confidence);
    }

    @Override
    public int hashCode() {
        return Objects.hash(score, probabilities, confidence);
    }

    @Override
    public String toString() {
        return "ScoreAnswer{score=" + score + ", probabilities=" + probabilities + ", confidence=" + confidence + '}';
    }

    public static final class Builder {

        private Double score;
        private final List<Double> probabilities = new ArrayList<>();
        private Double confidence;

        public Builder score(Double score) {
            this.score = score;
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

        public ScoreAnswer build() {
            return new ScoreAnswer(this);
        }
    }
}
