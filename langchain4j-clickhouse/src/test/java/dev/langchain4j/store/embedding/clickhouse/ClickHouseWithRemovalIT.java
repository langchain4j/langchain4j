package dev.langchain4j.store.embedding.clickhouse;

import com.clickhouse.data.ClickHouseDataType;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithRemovalIT;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.clickhouse.ClickHouseContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class ClickHouseWithRemovalIT extends EmbeddingStoreWithRemovalIT {

    private static final String USERNAME = "test-username";
    private static final String PASSWORD = "test-password";

    static ClickHouseContainer clickhouse = new ClickHouseContainer(DockerImageName.parse("clickhouse/clickhouse-server:latest"))
            .withDatabaseName("default")
            .withUsername(USERNAME)
            .withPassword(PASSWORD);

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    Map<String, ClickHouseDataType> metadataTypeMap = toMetadataTypeMap();

    ClickHouseSettings settings = ClickHouseSettings.builder()
            .url("http://" + clickhouse.getHost() + ":" + clickhouse.getMappedPort(8123))
            .table("langchain4j_" + ThreadLocalRandom.current().nextInt(0, Integer.MAX_VALUE))
            .username(USERNAME)
            .password(PASSWORD)
            .dimension(embeddingModel.dimension())
            .metadataTypeMap(metadataTypeMap)
            .build();

    ClickHouseEmbeddingStore embeddingStore = ClickHouseEmbeddingStore.builder()
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

    @AfterEach
    void afterEach() throws Exception {
        embeddingStore.close();
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    private Map<String, ClickHouseDataType> toMetadataTypeMap() {
        Map<String, ClickHouseDataType> metadataTypeMap = new HashMap<>();

        metadataTypeMap.put("type", ClickHouseDataType.String);

        return metadataTypeMap;
    }
}
