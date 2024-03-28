package dev.langchain4j.store.embedding.clickhouse;

import com.clickhouse.jdbc.ClickHouseDataSource;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.clickhouse.ClickHouseContainer;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

class ClickHouseEmbeddingStoreIT extends EmbeddingStoreIT {

    static ClickHouseContainer clickhouse = new ClickHouseContainer("clickhouse/clickhouse-server")
            .withDatabaseName("default")
            .withUsername("test-username")
            .withPassword("test-password");

    EmbeddingStore<TextSegment> embeddingStore = ClickHouseEmbeddingStore.builder()
            .url(clickhouse.getJdbcUrl())
            .username("test-username")
            .password("test-password")
            .dimension(384)
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

    @Override
    protected void clearStore() {
        DataSource dataSource = null;
        try {
            dataSource = new ClickHouseDataSource(clickhouse.getJdbcUrl(), new Properties());
        } catch (SQLException e) {
            e.printStackTrace();
        }

        try (Statement stmt = dataSource.getConnection("test-username", "test-password").createStatement()) {
            stmt.execute("DELETE FROM default.langchain4j_clickhouse_example WHERE id IS NOT NULL");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
