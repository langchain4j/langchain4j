package dev.langchain4j.store.embedding.elasticsearch;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;

import static dev.langchain4j.internal.Utils.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * For this test, because Elasticsearch container might not be super fast to start,
 * devs could prefer having a local cluster running already.
 * We try first to reach the local cluster and if not available, then start
 * a container with Testcontainers.
 */
abstract class AbstractElasticsearchEmbeddingStoreIT extends EmbeddingStoreWithFilteringIT {

    static ElasticsearchClientHelper elasticsearchClientHelper = new ElasticsearchClientHelper();

    private EmbeddingStore<TextSegment> embeddingStore;
    String indexName;

    @BeforeAll
    static void startServices() throws IOException {
        elasticsearchClientHelper.startServices();
        assertThat(elasticsearchClientHelper.restClient).isNotNull();
        assertThat(elasticsearchClientHelper.client).isNotNull();
    }

    @AfterAll
    static void stopServices() throws IOException {
        elasticsearchClientHelper.stopServices();
    }

    abstract ElasticsearchConfiguration withConfiguration();

    void optionallyCreateIndex(String indexName) throws IOException {
    }

    @BeforeEach
    void createEmbeddingStore() throws IOException {
        indexName = randomUUID();
        elasticsearchClientHelper.removeDataStore(indexName);
        optionallyCreateIndex(indexName);
        embeddingStore = ElasticsearchEmbeddingStore.builder()
                .configuration(withConfiguration())
                .restClient(elasticsearchClientHelper.restClient)
                .indexName(indexName)
                .build();
    }

    @AfterEach
    void removeDataStore() throws IOException {
        // We remove the indices in case we were running with a local test instance
        // we don't keep dirty things around
        elasticsearchClientHelper.removeDataStore(indexName);
    }

    private final EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @Override
    protected void ensureStoreIsEmpty() {
        // TODO fix
    }
}
