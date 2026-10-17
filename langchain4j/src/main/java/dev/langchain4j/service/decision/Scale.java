package dev.langchain4j.service.decision;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.decision.response.ScaleAnswer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The answer to a question of a decision service that places the input on an ordered scale: the mean level, the
 * probability of each level and, if the model reports one, a confidence. It is the typed counterpart of
 * {@link ScaleAnswer}.
 * <p>
 * The levels are the constants of the enum, from the lowest (the first declared constant) to the highest.
 *
 * @param <E> the enum whose constants are the levels
 * @since 1.21.0
 */
@Experimental
public final class Scale<E extends Enum<E>> {

    private final List<E> levels;
    private final Map<E, Double> probabilities;
    private final ScaleAnswer answer;

    private Scale(Builder<E> builder) {
        this.levels = List.of(ensureNotNull(builder.levelType, "levelType").getEnumConstants());
        Map<E, Double> probabilities = new EnumMap<>(builder.levelType);
        if (builder.probabilities != null) {
            builder.probabilities.forEach(
                    (level, probability) -> probabilities.put(ensureNotNull(level, "level"), probability));
        }
        this.probabilities = Collections.unmodifiableMap(probabilities);
        List<Double> probabilityList = new ArrayList<>();
        if (!probabilities.isEmpty()) {
            levels.forEach(level -> probabilityList.add(probabilities.getOrDefault(level, 0.0)));
        }
        this.answer = ScaleAnswer.builder()
                .mean(builder.mean)
                .probabilities(probabilityList)
                .confidence(builder.confidence)
                .build();
        double mean = answer.mean();
        if (mean < 0 || mean > levels.size() - 1) {
            throw new IllegalArgumentException(
                    "mean must be between 0 and %s, but was %s".formatted(levels.size() - 1, mean));
        }
    }

    /**
     * Creates a builder for a scale whose levels are the constants of the given enum.
     */
    public static <E extends Enum<E>> Builder<E> builder(Class<E> levelType) {
        return new Builder<>(levelType);
    }

    /**
     * The probability-weighted mean of the level indexes, from 0 (the first constant) to {@code n - 1} (the last
     * constant). It can fall between two levels: with the levels {@code LOW}, {@code MEDIUM} and {@code HIGH}, a
     * mean of 1.4 means "between medium and high, closer to medium".
     */
    public double mean() {
        return answer.mean();
    }

    /**
     * The level with the highest probability or, if the model does not report probabilities, the level closest to
     * {@link #mean()}.
     */
    public E mostLikely() {
        if (probabilities.isEmpty()) {
            return levels.get((int) Math.round(mean()));
        }
        return Collections.max(probabilities.entrySet(), Map.Entry.comparingByValue())
                .getKey();
    }

    /**
     * The probability of each level, from the lowest to the highest. Empty if the model does not report
     * probabilities.
     */
    public Map<E, Double> probabilities() {
        return probabilities;
    }

    /**
     * The probability of the given level, or 0 if the model reported probabilities but none for this level.
     *
     * @throws IllegalStateException if the model did not report probabilities.
     */
    public double probabilityOf(E level) {
        ensureProbabilities();
        return probabilities.getOrDefault(level, 0.0);
    }

    /**
     * The probability of the given level or any higher level, for example the probability that an incident is at
     * least of high severity.
     *
     * @throws IllegalStateException if the model did not report probabilities.
     */
    public double probabilityAtLeast(E level) {
        ensureProbabilities();
        return probabilities.entrySet().stream()
                .filter(entry -> entry.getKey().compareTo(level) >= 0)
                .mapToDouble(Map.Entry::getValue)
                .sum();
    }

    private void ensureProbabilities() {
        if (probabilities.isEmpty()) {
            throw new IllegalStateException("The model did not report probabilities");
        }
    }

    /**
     * How confident the model is in {@link #mean()}, from 0 to 1, or {@code null} if the model does not report a
     * confidence. The formula is defined by each model, so prefer {@link #probabilities()} for thresholds.
     */
    public Double confidence() {
        return answer.confidence();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Scale<?> that)) return false;
        return Objects.equals(levels, that.levels)
                && Objects.equals(probabilities, that.probabilities)
                && Objects.equals(answer, that.answer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(levels, probabilities, answer);
    }

    @Override
    public String toString() {
        return "Scale{mean=" + mean() + ", probabilities=" + probabilities + ", confidence=" + confidence() + '}';
    }

    public static final class Builder<E extends Enum<E>> {

        private final Class<E> levelType;
        private Double mean;
        private Map<E, Double> probabilities;
        private Double confidence;

        private Builder(Class<E> levelType) {
            this.levelType = levelType;
        }

        public Builder<E> mean(Double mean) {
            this.mean = mean;
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

        public Scale<E> build() {
            return new Scale<>(this);
        }
    }
}
