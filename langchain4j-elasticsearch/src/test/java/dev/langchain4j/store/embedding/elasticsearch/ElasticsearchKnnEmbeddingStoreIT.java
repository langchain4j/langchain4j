package dev.langchain4j.store.embedding.elasticsearch;

class ElasticsearchKnnEmbeddingStoreIT extends AbstractElasticsearchEmbeddingStoreIT {
    AbstractElasticsearchEmbeddingStore internalCreateEmbeddingStore(String indexName) {
        return ElasticsearchKnnEmbeddingStore.builder()
                .restClient(elasticsearchClientHelper.restClient)
                .indexName(indexName)
                .build();
    }
}
