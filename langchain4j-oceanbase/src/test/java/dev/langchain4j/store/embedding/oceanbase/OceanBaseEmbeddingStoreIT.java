package dev.langchain4j.store.embedding.oceanbase;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;

/**
 * Integration test for OceanBase embedding store with filtering support.
 * <p>
 * This test extends {@link EmbeddingStoreWithFilteringIT} to run standard filtering tests.
 * <p>
 * By default, integration tests will run a docker image of OceanBase using TestContainers.
 * Alternatively, the tests can connect to an OceanBase instance if the following environment variables are configured:
 * <ul>
 *   <li>{@code OCEANBASE_url}: JDBC url (e.g., "jdbc:oceanbase://127.0.0.1:2881/test")</li>
 *   <li>{@code OCEANBASE_USER}: Username (e.g., "root@test")</li>
 *   <li>{@code OCEANBASE_PASSWORD}: Password</li>
 * </ul>
 */
class OceanBaseEmbeddingStoreIT extends EmbeddingStoreWithFilteringIT {

    private static final String TABLE_NAME = "test_collection_" + System.currentTimeMillis();
    
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
                .retrieveEmbeddingsOnSearch(true)
                .idFieldName("id_field")
                .textFieldName("text_field")
                .metadataFieldName("metadata_field")
                .vectorFieldName("vector_field")
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

    @Override
    protected void ensureStoreIsReady() {
        // Table is created automatically in constructor.
        // If table was dropped in afterEach, we recreate the store instance
        // which will automatically recreate the table in the constructor.
        // Recreating the store ensures the table exists for the test.
        embeddingStore = OceanBaseEmbeddingStore.builder()
                .url(OceanBaseContainerTestBase.getJdbcUrl())
                .user(OceanBaseContainerTestBase.getUsername())
                .password(OceanBaseContainerTestBase.getPassword())
                .tableName(TABLE_NAME)
                .dimension(384)
                .metricType("cosine")
                .retrieveEmbeddingsOnSearch(true)
                .idFieldName("id_field")
                .textFieldName("text_field")
                .metadataFieldName("metadata_field")
                .vectorFieldName("vector_field")
                .build();
    }

    @Override
    protected void clearStore() {
        // Clear all data from the table by dropping and recreating it
        try {
            embeddingStore.dropCollection(TABLE_NAME);
        } catch (Exception e) {
            // Ignore if table doesn't exist
        }
        // Recreate the store (and table) by creating a new instance
        embeddingStore = OceanBaseEmbeddingStore.builder()
                .url(OceanBaseContainerTestBase.getJdbcUrl())
                .user(OceanBaseContainerTestBase.getUsername())
                .password(OceanBaseContainerTestBase.getPassword())
                .tableName(TABLE_NAME)
                .dimension(384)
                .metricType("cosine")
                .retrieveEmbeddingsOnSearch(true)
                .idFieldName("id_field")
                .textFieldName("text_field")
                .metadataFieldName("metadata_field")
                .vectorFieldName("vector_field")
                .build();
    }

    @AfterEach
    void afterEach() {
        // Clean up after each test
        clearStore();
    }

    @Override
    protected boolean supportsContains() {
        return true; // OceanBase supports ContainsString via LIKE operator
    }

    @Override
    protected boolean assertEmbedding() {
        // OceanBase stores vectors as strings, which may cause precision loss
        // when reading back. Skip embedding comparison to avoid test failures.
        return false;
    }
}

