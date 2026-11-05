package dev.langchain4j.store.embedding.pgvector;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PgVectorEmbeddingStoreWithSchemaIT {

    @Container
    static PostgreSQLContainer<?> pgVector = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    static DataSource dataSource;

    @BeforeAll
    static void beforeAll() throws SQLException {
        var source = new PGSimpleDataSource();
        source.setServerNames(new String[] {pgVector.getHost()});
        source.setPortNumbers(new int[] {pgVector.getFirstMappedPort()});
        source.setDatabaseName("test");
        source.setUser("test");
        source.setPassword("test");
        dataSource = source;
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE SCHEMA IF NOT EXISTS test_schema");
        }
    }

    @Test
    void should_create_column_metadata_indexes_when_table_has_schema() throws SQLException {
        MetadataStorageConfig config = DefaultMetadataStorageConfig.builder()
                .storageMode(MetadataStorageMode.COLUMN_PER_KEY)
                .columnDefinitions(List.of("key text NULL", "name text NULL"))
                .indexes(List.of("key", "name"))
                .build();

        PgVectorEmbeddingStore.datasourceBuilder()
                .datasource(dataSource)
                .table("test_schema.columns_table")
                .dimension(3)
                .dropTableFirst(true)
                .metadataStorageConfig(config)
                .build();

        assertThat(indexNames("columns_table")).contains("columns_table_key", "columns_table_name");
    }

    @Test
    void should_create_jsonb_metadata_indexes_when_table_has_schema() throws SQLException {
        MetadataStorageConfig config = DefaultMetadataStorageConfig.builder()
                .storageMode(MetadataStorageMode.COMBINED_JSONB)
                .columnDefinitions(List.of("metadata_b JSONB NULL"))
                .indexes(List.of("(metadata_b->'key')"))
                .indexType("GIN")
                .build();

        PgVectorEmbeddingStore.datasourceBuilder()
                .datasource(dataSource)
                .table("test_schema.jsonb_table")
                .dimension(3)
                .dropTableFirst(true)
                .metadataStorageConfig(config)
                .build();

        assertThat(indexNames("jsonb_table")).contains("jsonb_table_metadata_b_key");
    }

    private static List<String> indexNames(String table) throws SQLException {
        List<String> names = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT indexname FROM pg_indexes WHERE schemaname = 'test_schema' AND tablename = ?")) {
            statement.setString(1, table);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    names.add(resultSet.getString("indexname"));
                }
            }
        }
        return names;
    }
}
