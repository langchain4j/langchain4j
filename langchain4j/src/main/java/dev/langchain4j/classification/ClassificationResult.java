package dev.langchain4j.classification;

import java.util.Collection;

/**
 * Represent the result of classification.
 *
 * @param <L> The type of the label (e.g., String, Enum, etc.)
 */
public record ClassificationResult<L>(Collection<ScoredLabel<L>> scoredLabels) { // TODO collection type
}
