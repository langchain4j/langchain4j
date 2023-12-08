package dev.langchain4j.store.embedding.weaviate;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithoutMetadataIT;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static dev.langchain4j.internal.Utils.randomUUID;

@Testcontainers
class WeaviateEmbeddingStoreIT extends EmbeddingStoreWithoutMetadataIT {

    @Container
    static GenericContainer<?> weaviate = new GenericContainer<>("semitechnologies/weaviate:1.22.4")
            .withEnv("AUTHENTICATION_ANONYMOUS_ACCESS_ENABLED", "true")
            .withEnv("PERSISTENCE_DATA_PATH", "/var/lib/weaviate")
            .withEnv("QUERY_DEFAULTS_LIMIT", "25")
            .withEnv("DEFAULT_VECTORIZER_MODULE", "none")
            .withEnv("CLUSTER_HOSTNAME", "node1")
            .withExposedPorts(8080);

    EmbeddingStore<TextSegment> embeddingStore = WeaviateEmbeddingStore.builder()
            .apiKey("")
            .scheme("http")
            .host(String.format("%s:%d", weaviate.getHost(), weaviate.getMappedPort(8080)))
            .objectClass("Test" + randomUUID().replace("-", ""))
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
    protected void ensureStoreIsEmpty() {
        // TODO fix
    }
}