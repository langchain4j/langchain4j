package dev.langchain4j.classification;

import java.util.List;
import java.util.Objects;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Represent the result of classification.
 *
 * @param <L> The type of the label (e.g., String, Enum, etc.)
 */
public class ClassificationResult<L> {

    private final List<ScoredLabel<L>> scoredLabels;

    public ClassificationResult(List<ScoredLabel<L>> scoredLabels) {
        this.scoredLabels = ensureNotNull(scoredLabels, "scoredLabels");
    }

    public List<ScoredLabel<L>> scoredLabels() {
        return scoredLabels;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (ClassificationResult) obj;
        return Objects.equals(this.scoredLabels, that.scoredLabels);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scoredLabels);
    }

    @Override
    public String toString() {
        return "ClassificationResult {" +
                " scoredLabels = " + scoredLabels +
                " }";
    }
}
