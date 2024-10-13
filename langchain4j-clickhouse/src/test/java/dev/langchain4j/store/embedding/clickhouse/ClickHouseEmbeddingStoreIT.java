package dev.langchain4j.store.embedding.clickhouse;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.command.CommandResponse;
import com.clickhouse.data.ClickHouseDataType;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import dev.langchain4j.store.embedding.filter.Filter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.clickhouse.ClickHouseContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

class ClickHouseEmbeddingStoreIT extends EmbeddingStoreWithFilteringIT {

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

    @ParameterizedTest
    @MethodSource
    protected void should_filter_by_metadata(Filter metadataFilter,
                                             List<Metadata> matchingMetadatas,
                                             List<Metadata> notMatchingMetadatas) {
        refreshEmbeddingStore(matchingMetadatas, notMatchingMetadatas);

        super.should_filter_by_metadata(metadataFilter, matchingMetadatas, notMatchingMetadatas);
    }

    @ParameterizedTest
    @MethodSource
    protected void should_filter_by_metadata_not(Filter metadataFilter,
                                                 List<Metadata> matchingMetadatas,
                                                 List<Metadata> notMatchingMetadatas) {
        refreshEmbeddingStore(matchingMetadatas, notMatchingMetadatas);

        super.should_filter_by_metadata_not(metadataFilter, matchingMetadatas, notMatchingMetadatas);
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

    private Map<String, ClickHouseDataType> toMetadataTypeMap() {
        Map<String, Object> map = createMetadata().toMap();
        Map<String, ClickHouseDataType> metadataTypeMap = new HashMap<>();

        map.forEach((k, v) -> metadataTypeMap.put(k, toClickHouseType(k, v)));

        return metadataTypeMap;
    }

    private void refreshEmbeddingStore(List<Metadata> matchingMetadatas,
                                       List<Metadata> notMatchingMetadatas) {
        // Close old embeddingStore
        if (embeddingStore != null) {
            try {
                embeddingStore.close();
            } catch (Exception e) {
                // ignore
            }
        }

        // Drop old table
        dropTable();

        metadataTypeMap = new HashMap<>();
        matchingMetadatas.forEach(metadata -> {
            Map<String, Object> map = metadata.toMap();
            map.forEach((k, v) -> metadataTypeMap.put(k, toClickHouseType(k, v)));
        });
        notMatchingMetadatas.forEach(metadata -> {
            Map<String, Object> map = metadata.toMap();
            map.forEach((k, v) -> metadataTypeMap.put(k, toClickHouseType(k, v)));
        });

        settings = ClickHouseSettings.builder()
                .url("http://" + clickhouse.getHost() + ":" + clickhouse.getMappedPort(8123))
                .table("langchain4j_" + ThreadLocalRandom.current().nextInt(0, Integer.MAX_VALUE))
                .username(USERNAME)
                .password(PASSWORD)
                .dimension(embeddingModel.dimension())
                .metadataTypeMap(metadataTypeMap)
                .build();

        embeddingStore = ClickHouseEmbeddingStore.builder()
                .settings(settings)
                .build();
    }

    private ClickHouseDataType toClickHouseType(String key, Object value) {
        if (value instanceof String) {
            return ClickHouseDataType.String;
        }
        if (value instanceof Integer) {
            return ClickHouseDataType.Int32;
        }
        if (value instanceof Long) {
            return ClickHouseDataType.Int64;
        }
        // FIXME: Store as String because of the precision loss.
        if (value instanceof Float) {
            return ClickHouseDataType.String;
        }
        if (value instanceof Double) {
            return ClickHouseDataType.String;
        }
        if (value instanceof UUID) {
            return ClickHouseDataType.UUID;
        }

        throw new UnsupportedOperationException("Unsupported type: " + value.getClass().getName());
    }

    private void dropTable() {
        try (Client client = new Client.Builder()
                .addEndpoint(settings.getUrl())
                .setUsername(settings.getUsername())
                .setPassword(settings.getPassword())
                .serverSetting("allow_experimental_vector_similarity_index", "1")
                .build()) {

            CompletableFuture<CommandResponse> future = client.execute("DROP TABLE IF EXISTS " + settings.getTable());
            future.join();
        }
    }
}
