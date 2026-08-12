package dev.langchain4j.store.embedding.elasticsearch;

class ElasticsearchEmbeddingStoreScriptIT extends AbstractElasticsearchEmbeddingStoreIT {

    @Override
    ElasticsearchConfiguration withConfiguration() {
        // By default, Elasticsearch from 9.2 does not include the vector in the response
        // But the inherited tests are looking for the exact vectors
        // So we need to make sure that vectors are returned
        boolean includeVector = elasticsearchClientHelper.isGTENineTwo();
        return ElasticsearchConfigurationScript.builder()
                .includeVectorResponse(includeVector)
                .build();
    }
}
