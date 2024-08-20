package dev.langchain4j.store.embedding.elasticsearch;

class ElasticsearchEmbeddingStoreIT extends AbstractElasticsearchEmbeddingStoreIT {
    @Override
    ElasticsearchConfiguration withConfiguration() {
        return ElasticsearchConfigurationScript.builder().build();
    }
}
