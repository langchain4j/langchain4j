package dev.langchain4j.store.embedding.elasticsearch;

/**
 * For this test, because Elasticsearch container might not be super fast to start,
 * devs could prefer having a local cluster running already.
 * We try first to reach the local cluster and if not available, then start
 * a container with Testcontainers.
 */
class ElasticsearchKnnEmbeddingStoreIT extends AbstractElasticsearchEmbeddingStoreIT {
    AbstractElasticsearchEmbeddingStore internalCreateEmbeddingStore() {
        return ElasticsearchKnnEmbeddingStore.builder()
                .restClient(restClient)
                .indexName(indexName)
                .build();
    }
}
