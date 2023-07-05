package dev.langchain4j.store.embedding;

import dev.langchain4j.data.embedding.Embedding;

import java.util.Objects;

public class EmbeddingMatch<Embedded> {

    private final String embeddingId;
    private final Embedding embedding;
    private final Embedded embedded;
    private final Double score;

    public EmbeddingMatch(String embeddingId, Embedding embedding, Embedded embedded, Double score) {
        this.embeddingId = embeddingId;
        this.embedding = embedding;
        this.embedded = embedded;
        this.score = score;
    }

    public String embeddingId() {
        return embeddingId;
    }

    public Embedding embedding() {
        return embedding;
    }

    public Embedded embedded() {
        return embedded;
    }

    public Double score() {
        return score;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmbeddingMatch<?> that = (EmbeddingMatch<?>) o;
        return Objects.equals(this.embeddingId, that.embeddingId)
                && Objects.equals(this.embedding, that.embedding)
                && Objects.equals(this.embedded, that.embedded)
                && Objects.equals(this.score, that.score);
    }

    @Override
    public int hashCode() {
        return Objects.hash(embeddingId, embedding, embedded, score);
    }

    @Override
    public String toString() {
        return "EmbeddingMatch {" +
                " embeddingId = \"" + embeddingId + "\"" +
                ", embedding = " + embedding +
                ", embedded = " + embedded +
                ", score = " + score +
                " }";
    }
}
