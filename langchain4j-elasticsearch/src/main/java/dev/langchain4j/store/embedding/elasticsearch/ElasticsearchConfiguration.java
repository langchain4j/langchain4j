package dev.langchain4j.store.embedding.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;

import java.io.IOException;

public abstract class ElasticsearchConfiguration {
    abstract SearchResponse<Document> internalSearch(ElasticsearchClient client,
                                            String indexName,
                                            EmbeddingSearchRequest embeddingSearchRequest)
            throws ElasticsearchException, IOException;
}
