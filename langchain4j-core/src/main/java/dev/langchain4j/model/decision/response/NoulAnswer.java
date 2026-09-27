package dev.langchain4j.model.decision.response;

import dev.langchain4j.Experimental;
import java.util.Objects;

/**
 * The answer to a {@link dev.langchain4j.model.decision.request.NoulQuestion}: the {@link #probability()} that the
 * answer is "yes".
 *
 * @since 1.21.0
 */
@Experimental
public final class NoulAnswer implements DecisionAnswer {

    private final double probability;

    private NoulAnswer(Builder builder) {
        this.probability = Probabilities.ensureProbability(builder.probability, "probability");
    }

    /**
     * The probability that the answer is "yes", from 0 (certainly "no") to 1 (certainly "yes").
     */
    public double probability() {
        return probability;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NoulAnswer that)) return false;
        return Double.compare(probability, that.probability) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(probability);
    }

    @Override
    public String toString() {
        return "NoulAnswer{probability=" + probability + '}';
    }

    public static final class Builder {

        private Double probability;

        public Builder probability(Double probability) {
            this.probability = probability;
            return this;
        }

        public NoulAnswer build() {
            return new NoulAnswer(this);
        }
    }
}
