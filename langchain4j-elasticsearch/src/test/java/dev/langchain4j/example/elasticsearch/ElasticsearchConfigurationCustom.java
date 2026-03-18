package dev.langchain4j.example.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.elasticsearch.Document;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchConfiguration;
import java.io.IOException;

public class ElasticsearchConfigurationCustom implements ElasticsearchConfiguration {

    boolean customSearchCalled = false;

    // Custom implementation of vector search
    @Override
    public SearchResponse<Document> vectorSearch(
            ElasticsearchClient client, String indexName, EmbeddingSearchRequest embeddingSearchRequest)
            throws ElasticsearchException, IOException {
        customSearchCalled = true;
        return client.search(
                s -> s.index(indexName)
                        .query(q -> q.knn(k -> k.field(VECTOR_FIELD)
                                .queryVector(0.1f, 0.2f, 0.3f /* We force the vector to search for */)
                                .k(10))),
                Document.class);
    }
}
