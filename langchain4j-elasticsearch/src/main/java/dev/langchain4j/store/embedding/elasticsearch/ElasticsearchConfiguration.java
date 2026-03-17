package dev.langchain4j.store.embedding.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;

import java.io.IOException;

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
     * @return SearchResponse<Document> The search response
     * @throws ElasticsearchException if an error occurs during the search
     * @throws IOException            if an I/O error occurs
     */
    default SearchResponse<Document> vectorSearch(
            ElasticsearchClient client, String indexName, EmbeddingSearchRequest embeddingSearchRequest)
            throws ElasticsearchException, IOException {
        throw new UnsupportedOperationException(this.getClass().getSimpleName() + " configuration does not support vector search");
    }

    /**
     * Used for full text search
     *
     * @param client    The Elasticsearch client
     * @param indexName The index name
     * @param textQuery The text query
     * @return SearchResponse<Document> The search response
     * @throws ElasticsearchException if an error occurs during the search
     * @throws IOException            if an I/O error occurs
     */
    default SearchResponse<Document> fullTextSearch(ElasticsearchClient client, String indexName, String textQuery)
            throws ElasticsearchException, IOException {
        throw new UnsupportedOperationException(this.getClass().getSimpleName() + " configuration does not support fulltext search");
    }

    /**
     * Used for hybrid search
     *
     * @param client                 The Elasticsearch client
     * @param indexName              The index name
     * @param embeddingSearchRequest The embedding search request
     * @param textQuery              The text query
     * @return SearchResponse<Document> The search response
     * @throws ElasticsearchException if an error occurs during the search
     * @throws IOException            if an I/O error occurs
     */
    default SearchResponse<Document> hybridSearch(
            ElasticsearchClient client,
            String indexName,
            EmbeddingSearchRequest embeddingSearchRequest,
            String textQuery)
            throws ElasticsearchException, IOException {
        throw new UnsupportedOperationException(this.getClass().getSimpleName() + " configuration does not support hybrid search");
    }
}
