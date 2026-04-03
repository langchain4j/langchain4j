package dev.langchain4j.store.embedding.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import java.io.IOException;

public abstract class ElasticsearchConfiguration {
    static final String VECTOR_FIELD = "vector";
    static final String TEXT_FIELD = "text";

    boolean includeVectorResponse;

    /**
     * Used for vector search
     * @param client    The Elasticsearch client
     * @param indexName The index name
     * @param embeddingSearchRequest The embedding search request
     * @return SearchResponse<Document> The search response
     * @throws ElasticsearchException if an error occurs during the search
     * @throws IOException            if an I/O error occurs
     */
    abstract SearchResponse<Document> vectorSearch(
            ElasticsearchClient client, String indexName, EmbeddingSearchRequest embeddingSearchRequest)
            throws ElasticsearchException, IOException;

    /**
     * Used for full text search
     * @param client    The Elasticsearch client
     * @param indexName The index name
     * @param textQuery The text query
     * @return SearchResponse<Document> The search response
     * @throws ElasticsearchException if an error occurs during the search
     * @throws IOException            if an I/O error occurs
     */
    abstract SearchResponse<Document> fullTextSearch(ElasticsearchClient client, String indexName, String textQuery)
            throws ElasticsearchException, IOException;

    /**
     * Used for hybrid search
     * @param client                  The Elasticsearch client
     * @param indexName               The index name
     * @param embeddingSearchRequest  The embedding search request
     * @param textQuery               The text query
     * @return SearchResponse<Document> The search response
     * @throws ElasticsearchException if an error occurs during the search
     * @throws IOException            if an I/O error occurs
     */
    abstract SearchResponse<Document> hybridSearch(
            ElasticsearchClient client,
            String indexName,
            EmbeddingSearchRequest embeddingSearchRequest,
            String textQuery)
            throws ElasticsearchException, IOException;
}
