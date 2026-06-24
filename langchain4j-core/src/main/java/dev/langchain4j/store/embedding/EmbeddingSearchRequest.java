package dev.langchain4j.store.embedding;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureBetween;
import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.filter.Filter;
import java.util.Objects;

/**
 * Represents a request to search in an {@link EmbeddingStore}.
 */
public class EmbeddingSearchRequest {

    private final String query;
    private final Embedding queryEmbedding;
    private final int maxResults;
    private final double minScore;
    private final Filter filter;

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
     */
    public EmbeddingSearchRequest(Embedding queryEmbedding, Integer maxResults, Double minScore, Filter filter) {
        this(builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(maxResults)
                .minScore(minScore)
                .filter(filter));
    }

    /**
     * Creates an instance of an EmbeddingSearchRequest with an optional query text.
     * This constructor supports subclasses that pass query text for sparse/hybrid search,
     * where queryEmbedding may be null if query text is provided.
     *
     * @param queryEmbedding The embedding used as a reference. May be null if query is provided.
     * @param query          The query text used for sparse/hybrid search. May be null if queryEmbedding is provided.
     * @param maxResults     The maximum number of embeddings to return. Default: 3
     * @param minScore       The minimum score, ranging from 0 to 1 (inclusive). Default: 0
     * @param filter         The filter to be applied to the {@link Metadata} during search.
     */
    public EmbeddingSearchRequest(
            Embedding queryEmbedding, String query, Integer maxResults, Double minScore, Filter filter) {
        this(builder()
                .queryEmbedding(queryEmbedding)
                .query(query)
                .maxResults(maxResults)
                .minScore(minScore)
                .filter(filter));
    }

    /**
     * Creates an instance of an EmbeddingSearchRequest.
     *
     * @param builder The builder used to create the instance.
     */
    public EmbeddingSearchRequest(EmbeddingSearchRequestBuilder builder) {
        if (builder.queryEmbedding == null && builder.query == null) {
            throw new IllegalArgumentException("Either queryEmbedding or query must be provided");
        }
        this.query = builder.query;
        this.queryEmbedding = builder.queryEmbedding;
        this.maxResults = ensureGreaterThanZero(getOrDefault(builder.maxResults, 3), "maxResults");
        this.minScore = ensureBetween(getOrDefault(builder.minScore, 0.0), 0.0, 1.0, "minScore");
        this.filter = builder.filter;
    }

    public String query() {
        return query;
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

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        EmbeddingSearchRequest that = (EmbeddingSearchRequest) o;
        return maxResults == that.maxResults
                && Double.compare(minScore, that.minScore) == 0
                && Objects.equals(query, that.query)
                && Objects.equals(queryEmbedding, that.queryEmbedding)
                && Objects.equals(filter, that.filter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(query, queryEmbedding, maxResults, minScore, filter);
    }

    @Override
    public String toString() {
        return "EmbeddingSearchRequest{" + "query='"
                + query + '\'' + ", queryEmbedding="
                + queryEmbedding + ", maxResults="
                + maxResults + ", minScore="
                + minScore + ", filter="
                + filter + '}';
    }

    public static EmbeddingSearchRequestBuilder builder() {
        return new EmbeddingSearchRequestBuilder();
    }

    public static class EmbeddingSearchRequestBuilder {

        private String query;
        private Embedding queryEmbedding;
        private Integer maxResults;
        private Double minScore;
        private Filter filter;

        EmbeddingSearchRequestBuilder() {}

        /**
         * The query used for search.
         * This is an optional parameter that can be used by {@link EmbeddingStore} implementations to support hybrid search.
         */
        public EmbeddingSearchRequestBuilder query(String query) {
            this.query = query;
            return this;
        }

        /**
         * The embedding used as a reference. Found embeddings should be similar to this one.
         * This is a mandatory parameter.
         */
        public EmbeddingSearchRequestBuilder queryEmbedding(Embedding queryEmbedding) {
            this.queryEmbedding = queryEmbedding;
            return this;
        }

        /**
         * The maximum number of embeddings to return.
         * This is an optional parameter.
         * Default: 3
         */
        public EmbeddingSearchRequestBuilder maxResults(Integer maxResults) {
            this.maxResults = maxResults;
            return this;
        }

        /**
         * The minimum score, ranging from 0 to 1 (inclusive).
         * Only embeddings with a score &gt;= minScore will be returned.
         * This is an optional parameter.
         * Default: 0
         */
        public EmbeddingSearchRequestBuilder minScore(Double minScore) {
            this.minScore = minScore;
            return this;
        }

        /**
         * The filter to be applied to the {@link Metadata} during search.
         * Only {@link TextSegment}s whose {@link Metadata} matches the {@link Filter} will be returned.
         * Please note that not all {@link EmbeddingStore}s support this feature yet.
         * This is an optional parameter.
         * Default: no filtering
         */
        public EmbeddingSearchRequestBuilder filter(Filter filter) {
            this.filter = filter;
            return this;
        }

        public EmbeddingSearchRequest build() {
            return new EmbeddingSearchRequest(this);
        }
    }
}
