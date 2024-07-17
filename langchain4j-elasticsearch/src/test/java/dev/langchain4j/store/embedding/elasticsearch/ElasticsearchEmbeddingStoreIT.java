package dev.langchain4j.store.embedding.elasticsearch;

class ElasticsearchEmbeddingStoreIT extends AbstractElasticsearchEmbeddingStoreIT {
    ElasticsearchEmbeddingStore internalCreateEmbeddingStore(String indexName) {
        return ElasticsearchEmbeddingStore.builder()
                .configuration(new ElasticsearchConfigurationScript())
                .restClient(elasticsearchClientHelper.restClient)
                .indexName(indexName)
                .build();
    }
}
