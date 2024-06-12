package dev.langchain4j.store.embedding.elasticsearch;

class ElasticsearchKnnEmbeddingStoreIT extends AbstractElasticsearchEmbeddingStoreIT {
    AbstractElasticsearchEmbeddingStore internalCreateEmbeddingStore() {
        return ElasticsearchKnnEmbeddingStore.builder()
                .restClient(restClient)
                .indexName(indexName)
                .build();
    }
}
