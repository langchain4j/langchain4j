package dev.langchain4j.store.embedding.clickhouse;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.clickhouse.ClickHouseContainer;
import org.testcontainers.containers.BindMode;

/**
 * TODO
 */
public class ClickHouseEmbeddingStoreIT extends EmbeddingStoreIT {

    static ClickHouseContainer clickhouse = new ClickHouseContainer("clickhouse/clickhouse-server")
            .withFileSystemBind("", "/docker-entrypoint-initdb.d", BindMode.READ_ONLY);

    EmbeddingStore<TextSegment> embeddingStore = ClickHouseEmbeddingStore.builder()
            .build();
    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @BeforeAll
    static void beforeAll() {
        clickhouse.start();
    }

    @AfterAll
    static void afterAll() {
        clickhouse.stop();
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
