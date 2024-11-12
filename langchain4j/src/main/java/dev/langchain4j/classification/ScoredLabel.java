package dev.langchain4j.classification;

/**
 * Represents a classification label with score.
 *
 * @param <L> The type of the label (e.g., String, Enum, etc.)
 */
public record ScoredLabel<L>(L label, double score) {
}
