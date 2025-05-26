package dev.langchain4j.store.embedding;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.filter.Filter;

import java.util.Objects;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureBetween;
import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Represents a request to search in an {@link EmbeddingStore}.
 */
public class EmbeddingSearchRequest {

    private final Embedding queryEmbedding;
    private final int maxResults;
    private final double minScore;
    private final Filter filter;
    private final int searchMode; // 0 - dense (default), 1 - sparse, 2 - hybrid
    private final String queryText;

    /**
     * Creates an instance of an EmbeddingSearchRequest.
     *
     * @param queryEmbedding The embedding used as a reference. Found embeddings should be similar to this one.
     *                       This is a mandatory parameter.
     * @param maxResults     The maximum number of embeddings to return. This is an optional parameter. Default: 3
     * @param minScore       The minimum score, ranging from 0 to 1 (inclusive).
     *                       Only embeddings with a score &gt;= minScore will be returned.
     *                       This is an optional parameter. Default: 0
     * @param filter         The filter to be applied to the {@link Metadata} during search.
     *                       Only {@link TextSegment}s whose {@link Metadata}
     *                       matches the {@link Filter} will be returned.
     *                       Please note that not all {@link EmbeddingStore}s support this feature yet.
     *                       This is an optional parameter. Default: no filtering
     * @param searchMode     The search mode to be used. 0 - dense vector search, 1 - full-text search (sparse vector), 2 - hybrid search.
     *                       This is an optional parameter. Default: 0
     * @param queryText     The text of the query. This is the plain text used to do full-text search.
     */
    public EmbeddingSearchRequest(Embedding queryEmbedding, Integer maxResults, Double minScore, Filter filter, Integer searchMode, String queryText) {
        this.queryEmbedding = ensureNotNull(queryEmbedding, "queryEmbedding");
        this.maxResults = ensureGreaterThanZero(getOrDefault(maxResults, 3), "maxResults");
        this.minScore = ensureBetween(getOrDefault(minScore, 0.0), 0.0, 1.0, "minScore");
        this.filter = filter;
        this.searchMode = getOrDefault(searchMode, 0);
        this.queryText = queryText;
    }

    public static EmbeddingSearchRequestBuilder builder() {
        return new EmbeddingSearchRequestBuilder();
    }

    public Embedding queryEmbedding() {
        return queryEmbedding;
    }

    public int maxResults() {
        return maxResults;
    }

    public double minScore() {
        return minScore;
    }

    public Filter filter() {
        return filter;
    }

    public int searchMode() {
        return searchMode;
    }

    public String queryText() {
        return queryText;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof EmbeddingSearchRequest other)) return false;
        return this.maxResults == other.maxResults
                && this.minScore == other.minScore
                && Objects.equals(this.queryEmbedding, other.queryEmbedding)
                && Objects.equals(this.filter, other.filter)
                && this.searchMode == other.searchMode
                && Objects.equals(this.queryText, other.queryText);
    }

    public int hashCode() {
        return Objects.hash(queryEmbedding, maxResults, minScore, filter, searchMode, queryText);
    }

    public String toString() {
        return "EmbeddingSearchRequest(queryEmbedding=" + this.queryEmbedding + ", maxResults=" + this.maxResults + ", minScore=" + this.minScore + ", filter=" + this.filter + ", searchMode=" + this.searchMode + ", queryText=" + this.queryText + ")";
    }

    public static class EmbeddingSearchRequestBuilder {
        private Embedding queryEmbedding;
        private Integer maxResults;
        private Double minScore;
        private Filter filter;
        private Integer searchMode;
        private String queryText;

        EmbeddingSearchRequestBuilder() {
        }

        public EmbeddingSearchRequestBuilder queryEmbedding(Embedding queryEmbedding) {
            this.queryEmbedding = queryEmbedding;
            return this;
        }

        public EmbeddingSearchRequestBuilder maxResults(Integer maxResults) {
            this.maxResults = maxResults;
            return this;
        }

        public EmbeddingSearchRequestBuilder minScore(Double minScore) {
            this.minScore = minScore;
            return this;
        }

        public EmbeddingSearchRequestBuilder filter(Filter filter) {
            this.filter = filter;
            return this;
        }

        public EmbeddingSearchRequestBuilder searchMode(Integer searchMode) {
            this.searchMode = searchMode;
            return this;
        }

        public EmbeddingSearchRequestBuilder queryText(String queryText) {
            this.queryText = queryText;
            return this;
        }

        public EmbeddingSearchRequest build() {
            return new EmbeddingSearchRequest(this.queryEmbedding, this.maxResults, this.minScore, this.filter, this.searchMode, this.queryText);
        }
    }
}
