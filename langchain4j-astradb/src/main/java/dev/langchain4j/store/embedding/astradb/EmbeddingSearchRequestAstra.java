package dev.langchain4j.store.embedding.astradb;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.filter.Filter;

/**
 * This abstract class represents a search request for embeddings in AstraDB
 * A user can provide a field 'vectorize' that will be converted as a embedding on the spot and use for search or insertion
 */
public class EmbeddingSearchRequestAstra extends EmbeddingSearchRequest {

    /**
     * The field to vectorize
     */
    private final String vectorize;

    /**
     * Constructor leveraging default search request but with the vectorize options.
     * @param queryEmbedding
     *      vector or left empty to use the vectorize field
     * @param vectorize
     *      field to vectorize
     * @param maxResults
     *      maximum number of results to return
     * @param minScore
     *     minimum score to return
     * @param filter
     *      filter to apply
     */
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
