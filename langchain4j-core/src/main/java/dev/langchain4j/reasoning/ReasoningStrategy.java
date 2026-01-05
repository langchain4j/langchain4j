package dev.langchain4j.reasoning;

import static dev.langchain4j.internal.Utils.quoted;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.document.Metadata;
import java.util.Objects;

/**
 * Represents a generalizable reasoning strategy distilled from {@link ReasoningTrace}s.
 * <p>
 * A reasoning strategy captures the essence of how to approach a particular type
 * of problem, extracted from both successful and failed experiences. Unlike raw
 * traces, strategies are generalized and can be applied to similar but novel tasks.
 * <p>
 * Strategies are stored in a {@link ReasoningBank} and retrieved based on
 * similarity to new tasks, enabling agents to learn from past experiences.
 *
 * @since 1.11.0
 */
@Experimental
public class ReasoningStrategy {

    private final String taskPattern;
    private final String strategy;
    private final String pitfallsToAvoid;
    private final double confidenceScore;
    private final Metadata metadata;

    /**
     * Creates a new reasoning strategy.
     *
     * @param taskPattern     A pattern describing the type of tasks this strategy applies to.
     * @param strategy        The reasoning strategy itself - how to approach this type of task.
     * @param pitfallsToAvoid Common mistakes and pitfalls to avoid (learned from failures).
     * @param confidenceScore A score (0.0-1.0) indicating confidence in this strategy.
     * @param metadata        Additional metadata about the strategy.
     */
    public ReasoningStrategy(
            String taskPattern, String strategy, String pitfallsToAvoid, double confidenceScore, Metadata metadata) {
        this.taskPattern = ensureNotBlank(taskPattern, "taskPattern");
        this.strategy = ensureNotBlank(strategy, "strategy");
        this.pitfallsToAvoid = pitfallsToAvoid;
        this.confidenceScore = Math.max(0.0, Math.min(1.0, confidenceScore));
        this.metadata = metadata != null ? metadata : new Metadata();
    }

    /**
     * Returns the task pattern this strategy applies to.
     *
     * @return The task pattern.
     */
    public String taskPattern() {
        return taskPattern;
    }

    /**
     * Returns the reasoning strategy.
     *
     * @return The strategy.
     */
    public String strategy() {
        return strategy;
    }

    /**
     * Returns pitfalls to avoid when applying this strategy.
     *
     * @return Pitfalls to avoid, may be null.
     */
    public String pitfallsToAvoid() {
        return pitfallsToAvoid;
    }

    /**
     * Returns the confidence score for this strategy.
     *
     * @return A score between 0.0 and 1.0.
     */
    public double confidenceScore() {
        return confidenceScore;
    }

    /**
     * Returns the metadata associated with this strategy.
     *
     * @return The metadata.
     */
    public Metadata metadata() {
        return metadata;
    }

    /**
     * Formats this strategy for injection into a prompt.
     *
     * @return A formatted string representation suitable for prompt injection.
     */
    public String toPromptText() {
        StringBuilder sb = new StringBuilder();
        sb.append("Strategy for tasks like: ").append(taskPattern).append("\n");
        sb.append("Approach: ").append(strategy);
        if (pitfallsToAvoid != null && !pitfallsToAvoid.isBlank()) {
            sb.append("\nPitfalls to avoid: ").append(pitfallsToAvoid);
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReasoningStrategy that = (ReasoningStrategy) o;
        return Double.compare(confidenceScore, that.confidenceScore) == 0
                && Objects.equals(taskPattern, that.taskPattern)
                && Objects.equals(strategy, that.strategy)
                && Objects.equals(pitfallsToAvoid, that.pitfallsToAvoid)
                && Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(taskPattern, strategy, pitfallsToAvoid, confidenceScore, metadata);
    }

    @Override
    public String toString() {
        return "ReasoningStrategy{" + "taskPattern="
                + quoted(taskPattern) + ", strategy="
                + quoted(strategy) + ", pitfallsToAvoid="
                + quoted(pitfallsToAvoid) + ", confidenceScore="
                + confidenceScore + ", metadata="
                + metadata + '}';
    }

    /**
     * Creates a new builder for constructing ReasoningStrategy instances.
     *
     * @return A new builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a simple strategy with just task pattern and strategy text.
     *
     * @param taskPattern The task pattern.
     * @param strategy    The strategy.
     * @return A new ReasoningStrategy.
     */
    public static ReasoningStrategy from(String taskPattern, String strategy) {
        return builder().taskPattern(taskPattern).strategy(strategy).build();
    }

    /**
     * Builder for constructing ReasoningStrategy instances.
     */
    public static class Builder {

        private String taskPattern;
        private String strategy;
        private String pitfallsToAvoid;
        private double confidenceScore = 0.5;
        private Metadata metadata;

        public Builder taskPattern(String taskPattern) {
            this.taskPattern = taskPattern;
            return this;
        }

        public Builder strategy(String strategy) {
            this.strategy = strategy;
            return this;
        }

        public Builder pitfallsToAvoid(String pitfallsToAvoid) {
            this.pitfallsToAvoid = pitfallsToAvoid;
            return this;
        }

        public Builder confidenceScore(double confidenceScore) {
            this.confidenceScore = confidenceScore;
            return this;
        }

        public Builder metadata(Metadata metadata) {
            this.metadata = metadata;
            return this;
        }

        public ReasoningStrategy build() {
            return new ReasoningStrategy(taskPattern, strategy, pitfallsToAvoid, confidenceScore, metadata);
        }
    }
}
