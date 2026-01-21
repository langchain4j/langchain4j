package dev.langchain4j.store.embedding.opensearch;

import static dev.langchain4j.internal.Utils.randomUUID;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.opensearch.testcontainers.OpensearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

class OpenSearchEmbeddingStoreLocalIT extends EmbeddingStoreWithFilteringIT {

    /**
     * To run the tests locally, you don't need to have OpenSearch up-and-running. This implementation
     * uses TestContainers (https://testcontainers.com) and the built-in support for OpenSearch. Thus,
     * if you just execute the tests then a container will be spun up automatically for you.
     */
    @Container
    static OpensearchContainer<?> opensearch =
            new OpensearchContainer<>(DockerImageName.parse("opensearchproject/opensearch:2.10.0"));

    private EmbeddingStore<TextSegment> embeddingStore;

    private final EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @BeforeAll
    static void startOpenSearch() {
        opensearch.start();
    }

    @BeforeEach
    void createEmbeddingStore() {
        embeddingStore = OpenSearchEmbeddingStore.builder()
                .serverUrl(opensearch.getHttpHostAddress())
                .indexName(randomUUID())
                .build();
    }

    @AfterEach
    void removeDataStore() {
        embeddingStore.removeAll();
    }

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
        // Not needed since we create a new index for each test
    }
}
