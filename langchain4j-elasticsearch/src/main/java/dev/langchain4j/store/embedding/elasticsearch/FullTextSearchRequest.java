package dev.langchain4j.store.embedding.elasticsearch;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.filter.Filter;

/**
 * Represents a full text (non-vector) search request.
 *
 * @see ElasticsearchConfiguration#fullTextSearch(co.elastic.clients.elasticsearch.ElasticsearchClient, String, FullTextSearchRequest)
 */
public class FullTextSearchRequest {

    private final String textQuery;
    private final int maxResults;
    private final double minScore;
    private final Filter filter;

    public FullTextSearchRequest(Builder builder) {
        this.textQuery = ensureNotBlank(builder.textQuery, "textQuery");
        this.maxResults = ensureGreaterThanZero(getOrDefault(builder.maxResults, 3), "maxResults");
        this.minScore = getOrDefault(builder.minScore, 0.0);
        this.filter = builder.filter;
    }

    public String textQuery() {
        return textQuery;
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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String textQuery;
        private Integer maxResults;
        private Double minScore;
        private Filter filter;

        /**
         * The text to search for.
         * This is a mandatory parameter.
         */
        public Builder textQuery(String textQuery) {
            this.textQuery = textQuery;
            return this;
        }

        /**
         * The maximum number of results to return.
         * This is an optional parameter.
         * Default: 3
         */
        public Builder maxResults(Integer maxResults) {
            this.maxResults = maxResults;
            return this;
        }

        /**
         * The minimum relevance score. Only results with a score &gt;= minScore will be returned.
         * Unlike vector search, full text search scores are not normalized: they are
         * <a href="https://www.elastic.co/docs/reference/query-languages/query-dsl/query-filter-context">BM25 scores</a>
         * and can be greater than 1.
         * This is an optional parameter.
         * Default: 0
         */
        public Builder minScore(Double minScore) {
            this.minScore = minScore;
            return this;
        }

        /**
         * The filter to be applied to the {@link Metadata} during search.
         * Only {@link TextSegment}s whose {@link Metadata} matches the {@link Filter} will be returned.
         * This is an optional parameter.
         * Default: no filtering
         */
        public Builder filter(Filter filter) {
            this.filter = filter;
            return this;
        }

        public FullTextSearchRequest build() {
            return new FullTextSearchRequest(this);
        }
    }
}
