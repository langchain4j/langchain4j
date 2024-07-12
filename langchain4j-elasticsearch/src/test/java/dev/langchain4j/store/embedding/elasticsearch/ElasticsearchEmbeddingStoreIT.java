package dev.langchain4j.store.embedding.elasticsearch;

class ElasticsearchEmbeddingStoreIT extends AbstractElasticsearchEmbeddingStoreIT {
    AbstractElasticsearchEmbeddingStore internalCreateEmbeddingStore(String indexName) {
        return ElasticsearchEmbeddingStore.builder()
                .restClient(elasticsearchClientHelper.restClient)
                .indexName(indexName)
                .build();
    }
}
