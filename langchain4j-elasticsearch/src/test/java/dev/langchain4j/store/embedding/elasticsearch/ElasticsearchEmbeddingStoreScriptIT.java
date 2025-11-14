package dev.langchain4j.store.embedding.elasticsearch;

class ElasticsearchEmbeddingStoreScriptIT extends AbstractElasticsearchEmbeddingStoreIT {

    @Override
    protected ElasticsearchConfiguration withConfiguration() {
        return ElasticsearchConfigurationScript.builder().build();
    }
}
