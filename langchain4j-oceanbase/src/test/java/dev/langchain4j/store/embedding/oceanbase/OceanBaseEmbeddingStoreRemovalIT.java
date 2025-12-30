package dev.langchain4j.store.embedding.oceanbase;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithRemovalIT;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;

/**
 * Integration test for OceanBase embedding store removal operations.
 * <p>
 * This test extends {@link EmbeddingStoreWithRemovalIT} to run standard removal tests.
 * <p>
 * By default, integration tests will run a docker image of OceanBase using TestContainers.
 * Alternatively, the tests can connect to an OceanBase instance if the following environment variables are configured:
 * <ul>
 *   <li>{@code OCEANBASE_url}: JDBC url (e.g., "jdbc:oceanbase://127.0.0.1:2881/test")</li>
 *   <li>{@code OCEANBASE_USER}: Username (e.g., "root@test")</li>
 *   <li>{@code OCEANBASE_PASSWORD}: Password</li>
 * </ul>
 */
class OceanBaseEmbeddingStoreRemovalIT extends EmbeddingStoreWithRemovalIT {

    private static final String TABLE_NAME = "test_collection_removal_" + System.currentTimeMillis();
    
    private static OceanBaseEmbeddingStore embeddingStore;
    private static final EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @BeforeAll
    static void beforeAll() {
        OceanBaseContainerTestBase.initContainer();
        embeddingStore = OceanBaseEmbeddingStore.builder()
                .url(OceanBaseContainerTestBase.getJdbcUrl())
                .user(OceanBaseContainerTestBase.getUsername())
                .password(OceanBaseContainerTestBase.getPassword())
                .tableName(TABLE_NAME)
                .dimension(384)
                .metricType("cosine")
                .build();
    }

    @AfterAll
    static void afterAll() {
        OceanBaseContainerTestBase.stopContainer();
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @AfterEach
    void afterEach() {
        // Clean up after each test by dropping and recreating the table
        try {
            embeddingStore.dropCollection(TABLE_NAME);
        } catch (Exception e) {
            // Ignore cleanup errors
        }
        // Recreate the store (and table) by creating a new instance
        embeddingStore = OceanBaseEmbeddingStore.builder()
                .url(OceanBaseContainerTestBase.getJdbcUrl())
                .user(OceanBaseContainerTestBase.getUsername())
                .password(OceanBaseContainerTestBase.getPassword())
                .tableName(TABLE_NAME)
                .dimension(384)
                .metricType("cosine")
                .build();
    }

    @Override
    protected boolean supportsRemoveAllByFilter() {
        return false; // OceanBase doesn't support removeAll(Filter) yet
    }

    @Override
    protected boolean supportsRemoveAll() {
        return false; // OceanBase doesn't support removeAll() yet
    }
}

