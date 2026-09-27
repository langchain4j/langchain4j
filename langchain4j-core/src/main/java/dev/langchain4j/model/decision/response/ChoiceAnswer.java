package dev.langchain4j.model.decision.response;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import dev.langchain4j.Experimental;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The answer to a {@link dev.langchain4j.model.decision.request.ChoiceQuestion}: the chosen option, the probability
 * of each option and, if the model reports one, a confidence.
 *
 * @since 1.21.0
 */
@Experimental
public final class ChoiceAnswer implements DecisionAnswer {

    private final String choice;
    private final Map<String, Double> probabilities;
    private final Double confidence;

    private ChoiceAnswer(Builder builder) {
        this.choice = ensureNotBlank(builder.choice, "choice");
        this.probabilities = copy(builder.probabilities);
        this.confidence = Probabilities.ensureNullableProbability(builder.confidence, "confidence");
    }

    /**
     * The name of the chosen option, usually the one with the highest probability.
     */
    public String choice() {
        return choice;
    }

    /**
     * The probability of each option, keyed by option name. Empty if the model does not report probabilities.
     * <p>
     * Unlike {@link #confidence()}, probabilities mean the same thing for every model, which makes them the better
     * basis for thresholds, for example "escalate to a human when the two most likely options are close".
     */
    public Map<String, Double> probabilities() {
        return probabilities;
    }

    /**
     * How confident the model is in {@link #choice()}, from 0 to 1, or {@code null} if the model does not report
     * a confidence.
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
        if (!(o instanceof ChoiceAnswer that)) return false;
        return Objects.equals(choice, that.choice)
                && Objects.equals(probabilities, that.probabilities)
                && Objects.equals(confidence, that.confidence);
    }

    @Override
    public int hashCode() {
        return Objects.hash(choice, probabilities, confidence);
    }

    @Override
    public String toString() {
        return "ChoiceAnswer{choice=" + choice + ", probabilities=" + probabilities + ", confidence=" + confidence
                + '}';
    }

    public static final class Builder {

        private String choice;
        private final Map<String, Double> probabilities = new LinkedHashMap<>();
        private Double confidence;

        public Builder choice(String choice) {
            this.choice = choice;
            return this;
        }

        public Builder probabilities(Map<String, Double> probabilities) {
            this.probabilities.clear();
            if (probabilities != null) {
                probabilities.forEach(this::probability);
            }
            return this;
        }

        public Builder probability(String option, Double probability) {
            probabilities.put(
                    ensureNotBlank(option, "option"), Probabilities.ensureProbability(probability, "probability"));
            return this;
        }

        public Builder confidence(Double confidence) {
            this.confidence = confidence;
            return this;
        }

        public ChoiceAnswer build() {
            return new ChoiceAnswer(this);
        }
    }
}
