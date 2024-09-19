package dev.langchain4j.classification;

public class LabelWithScore<E> {

    private final E label;
    private final double score;

    public LabelWithScore(E label, double score) {
        this.label = label;
        this.score = score;
    }

    public E getLabel() {
        return label;
    }

    public double getScore() {
        return score;
    }
}
