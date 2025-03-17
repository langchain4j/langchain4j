package dev.langchain4j.store.embedding.opensearch;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import org.junit.jupiter.api.BeforeAll;
import org.opensearch.testcontainers.OpensearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

import static dev.langchain4j.internal.Utils.randomUUID;

class OpenSearchEmbeddingStoreLocalIT extends EmbeddingStoreIT {

    /**
     * To run the tests locally, you don't need to have OpenSearch up-and-running. This implementation
     * uses TestContainers (https://testcontainers.com) and the built-in support for OpenSearch. Thus,
     * if you just execute the tests then a container will be spun up automatically for you.
     */

    @Container
    static OpensearchContainer opensearch =
            new OpensearchContainer(DockerImageName.parse("opensearchproject/opensearch:2.10.0"));

    EmbeddingStore<TextSegment> embeddingStore = OpenSearchEmbeddingStore.builder()
            .serverUrl(opensearch.getHttpHostAddress())
            .indexName(randomUUID())
            .build();

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @BeforeAll
    static void startOpenSearch() {
        opensearch.start();
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
        // TODO fix
    }
}
