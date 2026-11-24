package dev.langchain4j.model.decision.response;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import dev.langchain4j.Experimental;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
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

    private final String value;
    private final Map<String, Double> probabilities;
    private final Double confidence;
    private final List<String> options;

    private ChoiceAnswer(Builder builder) {
        this.value = ensureNotBlank(builder.value, "value");
        this.probabilities = copy(builder.probabilities);
        this.confidence = Probabilities.ensureNullableProbability(builder.confidence, "confidence");
        this.options = copy(builder.options);
        if (!options.isEmpty() && !options.contains(value)) {
            throw new IllegalArgumentException("'%s' is not one of the options %s".formatted(value, options));
        }
    }

    /**
     * The name of the chosen option, usually the one with the highest probability.
     */
    public String value() {
        return value;
    }

    /**
     * The probability of each option, keyed by option name. Empty if the model does not report probabilities.
     * <p>
     * Unlike {@link #confidence()}, probabilities have the same meaning for every model, which makes them the better
     * basis for thresholds. Their calibration still differs between models, so thresholds need to be tuned again
     * when the model changes.
     */
    public Map<String, Double> probabilities() {
        return probabilities;
    }

    /**
     * The names of the options that were offered, in the order of the question.
     * <p>
     * They are set by the {@link dev.langchain4j.model.decision.DecisionModel} after checking the answer against the
     * question, so they are always present in a {@link DecisionResponse} returned by a decision model. They are empty
     * for an answer that was built without them.
     */
    public List<String> options() {
        return options;
    }

    /**
     * The probability of the given option, or 0 if the model reported probabilities but none for this option.
     *
     * @throws IllegalArgumentException if the option was not offered (see {@link #options()}), for example because
     *                                  its name is misspelled.
     * @throws IllegalStateException    if the model did not report probabilities.
     */
    public double probabilityOf(String option) {
        if (!options.isEmpty() && !options.contains(option)) {
            throw new IllegalArgumentException("'%s' is not one of the options %s".formatted(option, options));
        }
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
     * How confident the model is in {@link #value()}, from 0 to 1, or {@code null} if the model does not report
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
        return Objects.equals(value, that.value)
                && Objects.equals(probabilities, that.probabilities)
                && Objects.equals(confidence, that.confidence)
                && Objects.equals(options, that.options);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, probabilities, confidence, options);
    }

    @Override
    public String toString() {
        return "ChoiceAnswer{value=" + value + ", probabilities=" + probabilities + ", confidence=" + confidence
                + ", options=" + options + '}';
    }

    public static final class Builder {

        private String value;
        private final Map<String, Double> probabilities = new LinkedHashMap<>();
        private Double confidence;
        private List<String> options;

        public Builder value(String value) {
            this.value = value;
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

        /**
         * Sets the names of the options that were offered. Usually not needed: the
         * {@link dev.langchain4j.model.decision.DecisionModel} sets them after checking the answer against the question.
         */
        public Builder options(Collection<String> options) {
            this.options = options == null ? null : new ArrayList<>(options);
            return this;
        }

        public ChoiceAnswer build() {
            return new ChoiceAnswer(this);
        }
    }
}
