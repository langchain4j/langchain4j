package dev.langchain4j.store.embedding;

import dev.langchain4j.data.embedding.Embedding;

import java.util.Objects;
import java.util.Optional;

public class EmbeddingMatch<Embedded> {

    private final String embeddingId;
    private final Embedding embedding;
    private final Embedded embedded;

    public EmbeddingMatch(String embeddingId, Embedding embedding, Embedded embedded) {
        this.embeddingId = embeddingId;
        this.embedding = embedding;
        this.embedded = embedded;
    }

    public String embeddingId() {
        return embeddingId;
    }

    public Embedding embedding() {
        return embedding;
    }

    public Optional<Embedded> embedded() {
        return Optional.ofNullable(embedded);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmbeddingMatch<?> that = (EmbeddingMatch<?>) o;
        return Objects.equals(this.embeddingId, that.embeddingId)
                && Objects.equals(this.embedding, that.embedding)
                && Objects.equals(this.embedded, that.embedded);
    }

    @Override
    public int hashCode() {
        return Objects.hash(embeddingId, embedding, embedded);
    }

    @Override
    public String toString() {
        return "EmbeddingMatch {" +
                " embeddingId = \"" + embeddingId + "\"" +
                ", embedding = " + embedding +
                ", embedded = " + embedded +
                " }";
    }
}
