package dev.langchain4j.store.embedding.astradb;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.filter.Filter;


public class EmbeddingSearchRequestAstra extends EmbeddingSearchRequest {

    private final String vectorize;

    public EmbeddingSearchRequestAstra(Embedding queryEmbedding, String vectorize, Integer maxResults, Double minScore, Filter filter) {
        super(queryEmbedding != null ? queryEmbedding : Embedding.from(new float[0]), maxResults, minScore, filter);
        this.vectorize = vectorize;
    }

    /**
     * Retrieve the vectorize value.
     *
     * @return
     *      current vectorize value.
     */
    public String vectorize() {
        return vectorize;
    }
}
