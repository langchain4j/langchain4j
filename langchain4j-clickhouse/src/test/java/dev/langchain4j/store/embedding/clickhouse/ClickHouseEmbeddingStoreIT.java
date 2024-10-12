package dev.langchain4j.store.embedding.clickhouse;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.clickhouse.ClickHouseContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.HashMap;
import java.util.Map;

class ClickHouseEmbeddingStoreIT extends EmbeddingStoreIT {

    private static final String USERNAME = "test-username";
    private static final String PASSWORD = "test-password";

    static final Map<String, String> COLUMN_MAP = new HashMap<>();

    static {
        COLUMN_MAP.put("text", "text");
        COLUMN_MAP.put("id", "id");
        COLUMN_MAP.put("embedding", "embedding");
        COLUMN_MAP.put("metadata", "metadata");
    }

    static ClickHouseContainer clickhouse = new ClickHouseContainer(DockerImageName.parse("clickhouse/clickhouse-server:latest"))
            .withDatabaseName("default")
            .withUsername(USERNAME)
            .withPassword(PASSWORD);

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    ClickHouseSettings settings = ClickHouseSettings.builder()
            .url("http://" + clickhouse.getHost() + ":" + clickhouse.getMappedPort(8123))
            .username(USERNAME)
            .password(PASSWORD)
            .dimension(embeddingModel.dimension())
            .columnMap(COLUMN_MAP)
            .build();

    EmbeddingStore<TextSegment> embeddingStore = ClickHouseEmbeddingStore.builder()
            .settings(settings)
            .build();

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

    @Override
    protected void clearStore() {
        embeddingStore.removeAll();
    }
}
