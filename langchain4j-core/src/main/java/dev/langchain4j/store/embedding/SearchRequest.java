package dev.langchain4j.store.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.store.embedding.filter.MetadataFilter;
import lombok.Builder;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.*;

/**
 * TODO
 */
public class SearchRequest {

    private final Embedding queryEmbedding;
    private final int maxResults;
    private final double minScore;
    private final MetadataFilter metadataFilter; // TODO what if user wants to use his own type instead of TextSegment?

    @Builder
    public SearchRequest(Embedding queryEmbedding, Integer maxResults, Double minScore, MetadataFilter metadataFilter) {
        this.queryEmbedding = ensureNotNull(queryEmbedding, "queryEmbedding");
        this.maxResults = ensureGreaterThanZero(getOrDefault(maxResults, 3), "maxResults");
        this.minScore = ensureBetween(getOrDefault(minScore, 0.0), 0.0, 1.0, "minScore");
        this.metadataFilter = metadataFilter;
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

    public MetadataFilter metadataFilter() { // TODO optional?
        return metadataFilter;
    }
}
