package dev.langchain4j.classification;

public class ClassifyResult<L> {

    private final L label;
    private final double score;

    public ClassifyResult(L label, double score) {
        this.label = label;
        this.score = score;
    }

    public L label() {
        return label;
    }

    public double score() {
        return score;
    }
}
