package dev.langchain4j.classification;

import java.util.Collection;

/**
 * Represent a classification result.
 *
 * @param <L> Label type that is the result of classification.
 */
public class ClassificationResult<L> {

    private final Collection<ScoredLabel<L>> scoredLabels;

    public ClassificationResult(Collection<ScoredLabel<L>> scoredLabels) {
        this.scoredLabels = scoredLabels;
    }

    public Collection<ScoredLabel<L>> scoredLabels() {
        return scoredLabels;
    }

    public static <L> ClassificationResult<L> fromScoredLabels(Collection<ScoredLabel<L>> scoredLabels) {
        return new ClassificationResult<>(scoredLabels);
    }
}
