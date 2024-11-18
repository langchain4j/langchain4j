package dev.langchain4j.store.embedding;


import dev.langchain4j.data.embedding.Embedding;

/**
 * Represents a request to add object to {@link EmbeddingStore}.
 * @param <E> The class of the object that has been embedded. Typically, this is {@link dev.langchain4j.data.segment.TextSegment}.
 */
public class EmbeddingRecord<E> {
    private String id;
    private Embedding embedding;
    private E embedded;

    public EmbeddingRecord(final String id, final Embedding embedding, final E embedded) {
        this.id = id;
        this.embedding = embedding;
        this.embedded = embedded;
    }

    public static <E> EmbeddingAddRequestBuilder<E> builder() {
        return new EmbeddingAddRequestBuilder();
    }

    public String getId() {
        return this.id;
    }

    public Embedding getEmbedding() {
        return this.embedding;
    }

    public E getEmbedded() {
        return this.embedded;
    }

    public void setId(final String id) {
        this.id = id;
    }

    public void setEmbedding(final Embedding embedding) {
        this.embedding = embedding;
    }

    public void setEmbedded(final E embedded) {
        this.embedded = embedded;
    }

    public static class EmbeddingAddRequestBuilder<E> {

        private String id;
        private Embedding embedding;
        private E embedded;

        EmbeddingAddRequestBuilder() {
        }

        public EmbeddingAddRequestBuilder<E> id(final String id) {
            this.id = id;
            return this;
        }

        public EmbeddingAddRequestBuilder<E> embedding(final Embedding embedding) {
            this.embedding = embedding;
            return this;
        }

        public EmbeddingAddRequestBuilder<E> embedded(final E embedded) {
            this.embedded = embedded;
            return this;
        }

        public EmbeddingRecord<E> build() {
            return new EmbeddingRecord(this.id, this.embedding, this.embedded);
        }

        public String toString() {
            return "EmbeddingAddRequest.EmbeddingAddRequestBuilder(id=" + this.id + ", embedding=" + this.embedding + ", embedded=" + this.embedded + ")";
        }
    }


}
