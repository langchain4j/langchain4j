package dev.langchain4j.store.embedding.elasticsearch;

import java.io.IOException;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;

public abstract class ElasticsearchConfiguration {
    abstract SearchResponse<Document> internalSearch(ElasticsearchClient client,
                                                     String indexName,
                                                     EmbeddingSearchRequest embeddingSearchRequest)
            throws ElasticsearchException, IOException;

    abstract SearchResponse<Document> internalSearch(ElasticsearchClient client,
                                                     String indexName,
                                                     EmbeddingSearchRequest embeddingSearchRequest,
                                                     boolean includeVectorResponse)
            throws ElasticsearchException, IOException;

    abstract SearchResponse<Document> internalSearch(ElasticsearchClient client,
                                                     String indexName,
                                                     String textQuery)
            throws ElasticsearchException, IOException;

    abstract SearchResponse<Document> internalSearch(ElasticsearchClient client,
                                                     String indexName,
                                                     EmbeddingSearchRequest embeddingSearchRequest,
                                                     String textQuery,
                                                     boolean includeVectorResponse)
            throws ElasticsearchException, IOException;

}
