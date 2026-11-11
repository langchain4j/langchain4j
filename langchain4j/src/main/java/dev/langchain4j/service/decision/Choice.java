package dev.langchain4j.service.decision;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.decision.response.ChoiceAnswer;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The answer to a question of a decision service that chooses one of several options: the chosen option, the
 * probability of each option and, if the model reports one, a confidence. It is the typed counterpart of
 * {@link ChoiceAnswer}.
 * <p>
 * Decision services create it for enums, whose constants are the options. It can also be built for options that are
 * only known at runtime, such as strings, for example from a {@link ChoiceAnswer} of the {@code DecisionModel} API.
 *
 * @param <E> the type of the options: an enum whose constants are the options, or for example {@link String}
 * @since 1.21.0
 */
@Experimental
public final class Choice<E> {

    private final E value;
    private final Map<E, Double> probabilities;
    private final ChoiceAnswer answer;

    private Choice(Builder<E> builder) {
        this.value = ensureNotNull(builder.value, "value");
        Map<E, Double> probabilities = new LinkedHashMap<>();
        if (builder.probabilities != null) {
            builder.probabilities.forEach(
                    (option, probability) -> probabilities.put(ensureNotNull(option, "option"), probability));
        }
        this.probabilities = Collections.unmodifiableMap(probabilities);
        ChoiceAnswer.Builder answer = ChoiceAnswer.builder().value(name(value)).confidence(builder.confidence);
        probabilities.forEach((option, probability) -> answer.probability(name(option), probability));
        this.answer = answer.build();
    }

    private static String name(Object option) {
        return option instanceof Enum<?> constant ? constant.name() : String.valueOf(option);
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
    public double probabilityOf(E option) {
        return answer.probabilityOf(name(option));
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
        return answer.margin();
    }

    /**
     * How confident the model is in {@link #value()}, from 0 to 1, or {@code null} if the model does not report a
     * confidence. The formula is defined by each model, so prefer {@link #probabilities()} for thresholds.
     */
    public Double confidence() {
        return answer.confidence();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Choice<?> that)) return false;
        return Objects.equals(value, that.value)
                && Objects.equals(probabilities, that.probabilities)
                && Objects.equals(confidence(), that.confidence());
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, probabilities, confidence());
    }

    @Override
    public String toString() {
        return "Choice{value=" + value + ", probabilities=" + probabilities + ", confidence=" + confidence() + '}';
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
