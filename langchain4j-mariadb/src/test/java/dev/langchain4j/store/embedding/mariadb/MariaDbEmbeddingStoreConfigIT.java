package dev.langchain4j.store.embedding.mariadb;

import static dev.langchain4j.store.embedding.TestUtils.awaitUntilAsserted;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import dev.langchain4j.store.embedding.filter.Filter;
import java.sql.SQLException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
        String jdbcUrl = "%s?useBulkStmtsForInserts=false&connectionCollation=utf8mb4_bin&user=%s&password=%s&allowMultiQueries=true"
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

    @Test
    void sqlInjectionShouldBePrevented() {

        Embedding embedding = embeddingModel().embed("hello").content();
        embeddingStore().add(embedding);
        awaitUntilAsserted(() -> assertThat(getAllEmbeddings()).hasSize(1));

        Embedding referenceEmbedding = embeddingModel().embed("hi").content();

        Filter filter = metadataKey("key").isEqualTo("foo'; DROP TABLE " + TABLE_NAME + "; --");

        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(referenceEmbedding)
                .maxResults(1)
                .filter(filter)
                .build();

        try {
            embeddingStore().search(searchRequest);
        } catch (Exception e) {
            // ignore failure
        }

        // make sure table and embeddings are still there
        assertThat(getAllEmbeddings()).isNotEmpty();
    }
}
