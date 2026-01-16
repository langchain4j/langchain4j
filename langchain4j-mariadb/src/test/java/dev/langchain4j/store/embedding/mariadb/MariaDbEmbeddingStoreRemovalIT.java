package dev.langchain4j.store.embedding.mariadb;

import static org.testcontainers.shaded.org.apache.commons.lang3.RandomUtils.nextInt;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithRemovalIT;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.MariaDBContainer;

class MariaDbEmbeddingStoreRemovalIT extends EmbeddingStoreWithRemovalIT {
    static MariaDBContainer<?> mariadbContainer = MariaDbTestUtils.defaultContainer;

    final EmbeddingStore<TextSegment> embeddingStore = MariaDbEmbeddingStore.builder()
            .url(mariadbContainer.getJdbcUrl())
            .user(mariadbContainer.getUsername())
            .password(mariadbContainer.getPassword())
            .table("test" + nextInt(2000, 3000))
            .dimension(384)
            .createTable(true)
            .dropTableFirst(true)
            .build();

    final EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @BeforeAll
    static void beforeAll() {
        mariadbContainer.start();
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
