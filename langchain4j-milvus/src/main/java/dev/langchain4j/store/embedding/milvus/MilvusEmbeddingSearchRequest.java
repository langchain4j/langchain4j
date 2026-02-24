package dev.langchain4j.store.embedding.milvus;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.filter.Filter;

import java.util.Objects;

import static dev.langchain4j.internal.Utils.getOrDefault;

public class MilvusEmbeddingSearchRequest extends EmbeddingSearchRequest {
    private final SparseEmbedding sparseEmbedding;
    private final MilvusEmbeddingSearchMode searchMode; // DENSE (default), SPARSE, HYBRID
    // will auto-computed this query to sparse embedding using milvus supported built-in model
    // when sparseEmbedding is null and searchMode is SPARSE or HYBRID
    private final String sparseQueryText;

    public MilvusEmbeddingSearchRequest(
            Embedding queryEmbedding,
            SparseEmbedding sparseEmbedding,
            String sparseQueryText,
            Integer maxResults,
            Double minScore,
            Filter filter,
            MilvusEmbeddingSearchMode searchMode) {
        super(sparseQueryText, queryEmbedding, maxResults, minScore, filter, true);
        this.sparseEmbedding = sparseEmbedding;
        this.sparseQueryText = sparseQueryText;
        this.searchMode = getOrDefault(searchMode, MilvusEmbeddingSearchMode.DENSE);
    }


    public static MilvusEmbeddingSearchRequestBuilder milvusBuilder() {
        return new MilvusEmbeddingSearchRequestBuilder();
    }


    @Override
    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof MilvusEmbeddingSearchRequest other)) return false;
        return super.equals(other)
                && Objects.equals(this.sparseEmbedding, other.sparseEmbedding)
                && Objects.equals(this.sparseQueryText, other.sparseQueryText)
                && this.searchMode == other.searchMode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), sparseEmbedding, sparseQueryText, searchMode);
    }

    public MilvusEmbeddingSearchMode searchMode() {
        return searchMode;
    }

    public SparseEmbedding sparseEmbedding() {
        return sparseEmbedding;
    }

    public String sparseQueryText() {
        return sparseQueryText;
    }

    public static class MilvusEmbeddingSearchRequestBuilder {
        private Embedding queryEmbedding;
        private SparseEmbedding sparseEmbedding;
        private Integer maxResults;
        private Double minScore;
        private Filter filter;
        private MilvusEmbeddingSearchMode searchMode;
        private String sparseQueryText;

        MilvusEmbeddingSearchRequestBuilder() {}

        public MilvusEmbeddingSearchRequest.MilvusEmbeddingSearchRequestBuilder queryEmbedding(Embedding queryEmbedding) {
            this.queryEmbedding = queryEmbedding;
            return this;
        }

        public MilvusEmbeddingSearchRequest.MilvusEmbeddingSearchRequestBuilder maxResults(Integer maxResults) {
            this.maxResults = maxResults;
            return this;
        }

        public MilvusEmbeddingSearchRequest.MilvusEmbeddingSearchRequestBuilder minScore(Double minScore) {
            this.minScore = minScore;
            return this;
        }

        public MilvusEmbeddingSearchRequest.MilvusEmbeddingSearchRequestBuilder filter(Filter filter) {
            this.filter = filter;
            return this;
        }

        public MilvusEmbeddingSearchRequest.MilvusEmbeddingSearchRequestBuilder searchMode(MilvusEmbeddingSearchMode searchMode) {
            this.searchMode = searchMode;
            return this;
        }

        public MilvusEmbeddingSearchRequest.MilvusEmbeddingSearchRequestBuilder sparseEmbedding(SparseEmbedding sparseEmbedding) {
            this.sparseEmbedding = sparseEmbedding;
            return this;
        }

        public MilvusEmbeddingSearchRequest.MilvusEmbeddingSearchRequestBuilder sparseQueryText(String sparseQueryText) {
            this.sparseQueryText = sparseQueryText;
            return this;
        }

        public MilvusEmbeddingSearchRequest build() {
            return new MilvusEmbeddingSearchRequest(
                    this.queryEmbedding,
                    this.sparseEmbedding,
                    this.sparseQueryText,
                    this.maxResults,
                    this.minScore,
                    this.filter,
                    this.searchMode);
        }
    }
}
