package dev.langchain4j.store.embedding.mariadb;

import static org.testcontainers.shaded.org.apache.commons.lang3.RandomUtils.nextInt;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.MariaDBContainer;

class MariaDbEmbeddingStoreIT extends EmbeddingStoreWithFilteringIT {

    static MariaDBContainer<?> mariadbContainer = MariaDbTestUtils.defaultContainer;
    final EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
    EmbeddingStore<TextSegment> embeddingStore;

    @BeforeAll
    static void beforeAll() {
        mariadbContainer.start();
    }

    @Override
    protected void ensureStoreIsReady() {
        embeddingStore = MariaDbEmbeddingStore.builder()
                .url(mariadbContainer.getJdbcUrl())
                .user(mariadbContainer.getUsername())
                .password(mariadbContainer.getPassword())
                .table("test" + nextInt(1000, 2000))
                .dimension(384)
                .createTable(true)
                .dropTableFirst(true)
                .build();
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }
}
