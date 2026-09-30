package dev.langchain4j.model.decision.response;

import dev.langchain4j.Experimental;
import java.util.Objects;

/**
 * The answer to a {@link dev.langchain4j.model.decision.request.YesNoQuestion}: the {@link #probability()} that the
 * answer is "yes".
 *
 * @since 1.21.0
 */
@Experimental
public final class YesNoAnswer implements DecisionAnswer {

    private final double probability;

    private YesNoAnswer(Builder builder) {
        this.probability = Probabilities.ensureProbability(builder.probability, "probability");
    }

    /**
     * The probability that the answer is "yes", from 0 (certainly "no") to 1 (certainly "yes").
     */
    public double probability() {
        return probability;
    }

    /**
     * Returns {@code true} if the probability of "yes" is greater than or equal to the given threshold.
     */
    public boolean isYes(double threshold) {
        return probability >= Probabilities.ensureProbability(threshold, "threshold");
    }

    /**
     * Creates an answer with the given probability of "yes", from 0 to 1.
     */
    public static YesNoAnswer of(double probability) {
        return builder().probability(probability).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof YesNoAnswer that)) return false;
        return Double.compare(probability, that.probability) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(probability);
    }

    @Override
    public String toString() {
        return "YesNoAnswer{probability=" + probability + '}';
    }

    public static final class Builder {

        private Double probability;

        public Builder probability(Double probability) {
            this.probability = probability;
            return this;
        }

        public YesNoAnswer build() {
            return new YesNoAnswer(this);
        }
    }
}
