package dev.langchain4j.service.decision;

import static dev.langchain4j.internal.ValidationUtils.ensureTrue;

import dev.langchain4j.Experimental;
import java.util.Objects;

/**
 * The answer to a yes/no question of a decision service: the {@link #probability()} that the answer is "yes".
 * <p>
 * Use it instead of {@code boolean} to decide on a threshold in code, or to evaluate several thresholds without
 * calling the model again.
 *
 * @since 1.21.0
 */
@Experimental
public final class YesNo {

    private final double probability;

    private YesNo(double probability) {
        this.probability = ensureProbability(probability, "probability");
    }

    /**
     * Creates an answer with the given probability of "yes", from 0 to 1.
     */
    public static YesNo of(double probability) {
        return new YesNo(probability);
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
        return probability >= ensureProbability(threshold, "threshold");
    }

    static double ensureProbability(double value, String name) {
        ensureTrue(value >= 0 && value <= 1, name + " must be between 0 and 1, but was " + value);
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof YesNo that)) return false;
        return Double.compare(probability, that.probability) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(probability);
    }

    @Override
    public String toString() {
        return "YesNo{probability=" + probability + '}';
    }
}
