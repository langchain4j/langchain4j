package dev.langchain4j.store.embedding.elasticsearch;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import lombok.SneakyThrows;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static dev.langchain4j.internal.Utils.randomUUID;

@Testcontainers
class ElasticsearchEmbeddingStoreIT extends EmbeddingStoreIT {

    @Container
    private static final ElasticsearchContainer elasticsearch = new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:8.9.0")
            .withEnv("xpack.security.enabled", "false");

    EmbeddingStore<TextSegment> embeddingStore = ElasticsearchEmbeddingStore.builder()
            .serverUrl(elasticsearch.getHttpHostAddress())
            .indexName(randomUUID())
            .dimension(384)
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

    @Override
    @SneakyThrows
    protected void awaitUntilPersisted() {
        Thread.sleep(1000);
    }
}
