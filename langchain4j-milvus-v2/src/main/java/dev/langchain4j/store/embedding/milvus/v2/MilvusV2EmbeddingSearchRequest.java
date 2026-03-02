package dev.langchain4j.store.embedding.milvus.v2;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.filter.Filter;

import java.util.Objects;

public class MilvusV2EmbeddingSearchRequest extends EmbeddingSearchRequest {
    private final SparseEmbedding sparseEmbedding;

    public MilvusV2EmbeddingSearchRequest(
            Embedding queryEmbedding,
            SparseEmbedding sparseEmbedding,
            String query,
            Integer maxResults,
            Double minScore,
            Filter filter) {
        super(queryEmbedding, query, maxResults, minScore, filter);
        this.sparseEmbedding = sparseEmbedding;
    }

    public static MilvusV2EmbeddingSearchRequestBuilder milvusBuilder() {
        return new MilvusV2EmbeddingSearchRequestBuilder();
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof MilvusV2EmbeddingSearchRequest other)) return false;
        return super.equals(other)
                && Objects.equals(this.sparseEmbedding, other.sparseEmbedding);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), sparseEmbedding);
    }

    public SparseEmbedding sparseEmbedding() {
        return sparseEmbedding;
    }

    public static class MilvusV2EmbeddingSearchRequestBuilder {
        private Embedding queryEmbedding;
        private SparseEmbedding sparseEmbedding;
        private Integer maxResults;
        private Double minScore;
        private Filter filter;
        private String query;

        MilvusV2EmbeddingSearchRequestBuilder() {}

        public MilvusV2EmbeddingSearchRequestBuilder queryEmbedding(Embedding queryEmbedding) {
            this.queryEmbedding = queryEmbedding;
            return this;
        }

        public MilvusV2EmbeddingSearchRequestBuilder maxResults(Integer maxResults) {
            this.maxResults = maxResults;
            return this;
        }

        public MilvusV2EmbeddingSearchRequestBuilder minScore(Double minScore) {
            this.minScore = minScore;
            return this;
        }

        public MilvusV2EmbeddingSearchRequestBuilder filter(Filter filter) {
            this.filter = filter;
            return this;
        }

        public MilvusV2EmbeddingSearchRequestBuilder sparseEmbedding(SparseEmbedding sparseEmbedding) {
            this.sparseEmbedding = sparseEmbedding;
            return this;
        }

        public MilvusV2EmbeddingSearchRequestBuilder query(String query) {
            this.query = query;
            return this;
        }

        public MilvusV2EmbeddingSearchRequest build() {
            return new MilvusV2EmbeddingSearchRequest(
                    this.queryEmbedding,
                    this.sparseEmbedding,
                    this.query,
                    this.maxResults,
                    this.minScore,
                    this.filter);
        }
    }
}
