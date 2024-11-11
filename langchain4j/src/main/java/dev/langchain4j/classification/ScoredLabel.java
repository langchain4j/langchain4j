package dev.langchain4j.classification;

/**
 * Represent a classification label with score.
 *
 * @param <L> The type of the label (e.g., String, Enum, etc.)
 */
public record ScoredLabel<L>(L label, double score) {
}
