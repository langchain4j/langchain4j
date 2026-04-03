package dev.langchain4j.classification;

import static dev.langchain4j.internal.Utils.quoted;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import java.util.Objects;

/**
 * Represents a classification label with score.
 *
 * @param <L> The type of the label (e.g., String, Enum, etc.)
 */
public class ScoredLabel<L> {

    private final L label;
    private final double score;

    public ScoredLabel(L label, double score) {
        this.label = ensureNotNull(label, "label");
        this.score = score;
    }

    public L label() {
        return label;
    }

    public double score() {
        return score;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ScoredLabel<?> that)) return false;
        return Objects.equals(this.label, that.label)
                && Double.doubleToLongBits(this.score) == Double.doubleToLongBits(that.score);
    }

    @Override
    public int hashCode() {
        return Objects.hash(label, score);
    }

    @Override
    public String toString() {
        return "ScoredLabel {" + " label = " + quoted(label) + ", score = " + score + " }";
    }
}
