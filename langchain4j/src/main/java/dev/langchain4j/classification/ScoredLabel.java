package dev.langchain4j.classification;

/**
 * Represent a classification label with score.
 *
 * @param <L> Label type that is the result of classification.
 */
public class ScoredLabel<L> {

    private final L label;
    private final double score;

    public ScoredLabel(L label, double score) {
        this.label = label;
        this.score = score;
    }

    public L label() {
        return label;
    }

    public double score() {
        return score;
    }

    public static <L> ScoredLabel<L> from(L label, double score) {
        return new ScoredLabel<>(label, score);
    }
}
