package dev.langchain4j.store.embedding.weaviate;

import static dev.langchain4j.internal.Utils.randomUUID;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.weaviate.WeaviateContainer;

/**
 * Runs against the oldest Weaviate version that the Weaviate client supports.
 */
@Testcontainers
class LocalWeaviateMinimumVersionEmbeddingStoreIT extends EmbeddingStoreIT {

    @Container
    static WeaviateContainer weaviate = new WeaviateContainer("semitechnologies/weaviate:1.32.0")
            .withEnv("QUERY_DEFAULTS_LIMIT", "25")
            .withEnv("DEFAULT_VECTORIZER_MODULE", "none")
            .withEnv("CLUSTER_HOSTNAME", "node1");

    private final EmbeddingStore<TextSegment> embeddingStore = WeaviateEmbeddingStore.builder()
            .scheme("http")
            .host(weaviate.getHost())
            .port(weaviate.getFirstMappedPort())
            .grpcPort(weaviate.getMappedPort(50051))
            .objectClass("Test" + randomUUID().replace("-", ""))
            .metadataKeys(LocalWeaviateEmbeddingStoreIT.METADATA_KEYS)
            .build();

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @Override
    protected void clearStore() {
        embeddingStore.removeAll();
    }
}
