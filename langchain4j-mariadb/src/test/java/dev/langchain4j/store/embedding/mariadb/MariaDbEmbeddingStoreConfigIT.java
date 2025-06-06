package dev.langchain4j.store.embedding.mariadb;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import java.sql.SQLException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.mariadb.jdbc.MariaDbPoolDataSource;
import org.testcontainers.containers.MariaDBContainer;

abstract class MariaDbEmbeddingStoreConfigIT extends EmbeddingStoreWithFilteringIT {
    static MariaDBContainer<?> mariadbContainer = MariaDbTestUtils.defaultContainer;

    static EmbeddingStore<TextSegment> embeddingStore;

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    static MariaDbPoolDataSource dataSource;

    static final String TABLE_NAME = "test";
    static final int TABLE_DIMENSION = 384;

    static void configureStore(MetadataStorageConfig config) {
        mariadbContainer.start();
        String jdbcUrl = "%s?useBulkStmtsForInserts=false&connectionCollation=utf8mb4_bin&user=%s&password=%s"
                .formatted(
                        mariadbContainer.getJdbcUrl(), mariadbContainer.getUsername(), mariadbContainer.getPassword());
        try {
            dataSource = new MariaDbPoolDataSource(jdbcUrl);
            embeddingStore = MariaDbEmbeddingStore.builder()
                    .datasource(dataSource)
                    .table(TABLE_NAME)
                    .dimension(TABLE_DIMENSION)
                    .createTable(true)
                    .dropTableFirst(true)
                    .metadataStorageConfig(config)
                    .build();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @AfterAll
    static void afterAll() {
        dataSource.close();
    }

    @BeforeEach
    void beforeEach() {
        try (var connection = dataSource.getConnection()) {
            connection.createStatement().executeUpdate("TRUNCATE TABLE %s".formatted(TABLE_NAME));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void ensureStoreIsEmpty() {
        // it's not necessary to clear the store before every test
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
    protected boolean testFloatExactly() {
        return false;
    }
}
