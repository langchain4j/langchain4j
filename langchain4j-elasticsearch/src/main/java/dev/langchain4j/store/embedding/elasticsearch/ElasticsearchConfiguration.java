package dev.langchain4j.store.embedding.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import java.io.IOException;
import org.slf4j.LoggerFactory;

public interface ElasticsearchConfiguration {
    String VECTOR_FIELD = "vector";
    String TEXT_FIELD = "text";

    /**
     * Temporary method which returns if we should return the Vector in the response
     * @return true or false
     */
    default boolean isIncludeVectorResponse() {
        return false;
    }

    /**
     * Used for vector search
     *
     * @param client                 The Elasticsearch client
     * @param indexName              The index name
     * @param embeddingSearchRequest The embedding search request
     * @return The search response
     * @throws ElasticsearchException if an error occurs during the search
     * @throws IOException            if an I/O error occurs
     */
    default SearchResponse<Document> vectorSearch(
            ElasticsearchClient client, String indexName, EmbeddingSearchRequest embeddingSearchRequest)
            throws ElasticsearchException, IOException {
        throw new UnsupportedOperationException(
                this.getClass().getSimpleName() + " configuration does not support vector search");
    }

    /**
     * Used for full text search
     *
     * @param client    The Elasticsearch client
     * @param indexName The index name
     * @param textQuery The text query
     * @return The search response
     * @throws ElasticsearchException if an error occurs during the search
     * @throws IOException            if an I/O error occurs
     * @deprecated Use {@link #fullTextSearch(ElasticsearchClient, String, FullTextSearchRequest)} instead.
     * It also applies the {@code maxResults}, {@code minScore} and {@code filter} of the request.
     */
    @Deprecated(forRemoval = true)
    default SearchResponse<Document> fullTextSearch(ElasticsearchClient client, String indexName, String textQuery)
            throws ElasticsearchException, IOException {
        throw new UnsupportedOperationException(
                this.getClass().getSimpleName() + " configuration does not support fulltext search");
    }

    /**
     * Used for full text search.
     *
     * @param client    The Elasticsearch client
     * @param indexName The index name
     * @param request   The full text search request
     * @return The search response
     * @throws ElasticsearchException if an error occurs during the search
     * @throws IOException            if an I/O error occurs
     */
    default SearchResponse<Document> fullTextSearch(
            ElasticsearchClient client, String indexName, FullTextSearchRequest request)
            throws ElasticsearchException, IOException {
        if (request.filter() != null) {
            LoggerFactory.getLogger(ElasticsearchConfiguration.class)
                    .warn(
                            "[{}] does not implement fullTextSearch(ElasticsearchClient, String, FullTextSearchRequest), "
                                    + "so the filter, maxResults and minScore are ignored and documents which do not match the filter can be returned.",
                            this.getClass().getName());
        }
        return fullTextSearch(client, indexName, request.textQuery());
    }

    /**
     * Used for hybrid search
     *
     * @param client                 The Elasticsearch client
     * @param indexName              The index name
     * @param embeddingSearchRequest The embedding search request
     * @param textQuery              The text query
     * @return The search response
     * @throws ElasticsearchException if an error occurs during the search
     * @throws IOException            if an I/O error occurs
     */
    default SearchResponse<Document> hybridSearch(
            ElasticsearchClient client,
            String indexName,
            EmbeddingSearchRequest embeddingSearchRequest,
            String textQuery)
            throws ElasticsearchException, IOException {
        throw new UnsupportedOperationException(
                this.getClass().getSimpleName() + " configuration does not support hybrid search");
    }
}
