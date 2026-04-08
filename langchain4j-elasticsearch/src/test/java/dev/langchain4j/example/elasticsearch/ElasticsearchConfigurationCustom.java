package dev.langchain4j.example.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.elasticsearch.Document;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchConfiguration;
import java.util.Map;

public class ElasticsearchConfigurationCustom implements ElasticsearchConfiguration {

    boolean customSearchCalled = false;

    // Custom implementation of vector search
    @Override
    public SearchResponse<Document> vectorSearch(
            ElasticsearchClient client, String indexName, EmbeddingSearchRequest embeddingSearchRequest)
            throws ElasticsearchException {
        customSearchCalled = true;

        // We build from scratch a fake SearchResponse without even calling Elasticsearch
        return new SearchResponse.Builder<Document>()
                .took(1L)
                .timedOut(false)
                .shards(s -> s.total(1).successful(1).skipped(0).failed(0))
                .hits(hs -> hs.total(t -> t.value(1L).relation(TotalHitsRelation.Eq))
                        .hits(hs2 -> hs2.id("my-fake-doc")
                                .index("my-fake-index")
                                .score(1.0)
                                .source(Document.builder()
                                        .vector(new float[] {0.1f, 0.2f, 0.3f})
                                        .text("This is a fake document.")
                                        .metadata(Map.of())
                                        .build())))
                .build();
    }
}
