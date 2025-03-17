package dev.langchain4j.store.embedding.elasticsearch;

class ElasticsearchEmbeddingStoreScriptIT extends AbstractElasticsearchEmbeddingStoreIT {

    @Override
    ElasticsearchConfiguration withConfiguration() {
        return ElasticsearchConfigurationScript.builder().build();
    }
}
