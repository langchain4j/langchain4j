package dev.langchain4j.store.embedding.elasticsearch;

import dev.langchain4j.store.embedding.elasticsearch.test.condition.DisabledOnWindowsCIRequiringContainer;

@DisabledOnWindowsCIRequiringContainer
class ElasticsearchEmbeddingStoreScriptIT extends AbstractElasticsearchEmbeddingStoreIT {

    @Override
    ElasticsearchConfiguration withConfiguration() {
        return ElasticsearchConfigurationScript.builder().build();
    }
}
