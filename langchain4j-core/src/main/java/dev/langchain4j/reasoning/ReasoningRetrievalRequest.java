package dev.langchain4j.reasoning;

import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.embedding.Embedding;

/**
 * A request to retrieve {@link ReasoningStrategy} instances from a {@link ReasoningBank}.
 *
 * @since 1.11.0
 */
@Experimental
public class ReasoningRetrievalRequest {

    private final Embedding queryEmbedding;
    private final int maxResults;
    private final double minScore;

    private ReasoningRetrievalRequest(Builder builder) {
        this.queryEmbedding = ensureNotNull(builder.queryEmbedding, "queryEmbedding");
        this.maxResults = ensureGreaterThanZero(builder.maxResults, "maxResults");
        this.minScore = builder.minScore;
    }

    /**
     * Returns the query embedding to search for similar strategies.
     *
     * @return The query embedding.
     */
    public Embedding queryEmbedding() {
        return queryEmbedding;
    }

    /**
     * Returns the maximum number of results to return.
     *
     * @return The maximum results.
     */
    public int maxResults() {
        return maxResults;
    }

    /**
     * Returns the minimum similarity score threshold.
     *
     * @return The minimum score (0.0-1.0).
     */
    public double minScore() {
        return minScore;
    }

    /**
     * Creates a new builder.
     *
     * @return A new builder instance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing ReasoningRetrievalRequest instances.
     */
    public static class Builder {

        private Embedding queryEmbedding;
        private int maxResults = 3;
        private double minScore = 0.0;

        public Builder queryEmbedding(Embedding queryEmbedding) {
            this.queryEmbedding = queryEmbedding;
            return this;
        }

        public Builder maxResults(int maxResults) {
            this.maxResults = maxResults;
            return this;
        }

        public Builder minScore(double minScore) {
            this.minScore = minScore;
            return this;
        }

        public ReasoningRetrievalRequest build() {
            return new ReasoningRetrievalRequest(this);
        }
    }
}
