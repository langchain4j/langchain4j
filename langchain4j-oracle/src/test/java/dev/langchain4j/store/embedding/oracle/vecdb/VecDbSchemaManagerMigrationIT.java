package dev.langchain4j.store.embedding.oracle.vecdb;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.oracle.CreateOption;
import dev.langchain4j.store.embedding.oracle.vecdb.enums.VecDbApiVersion;
import dev.langchain4j.store.embedding.oracle.vecdb.enums.VecDbDistanceMetric;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Verifies physical migration of an earlier VecDB table layout on a newer Oracle Database. */
@Testcontainers(disabledWithoutDocker = true)
class VecDbSchemaManagerMigrationIT {

    private static final String TABLE_NAME = "LC4J_VECDB_MIGRATION_IT";

    @BeforeEach
    void createCurrentVectorTable() throws SQLException {
        try (Connection connection = VecDbTestOperations.dataSource().getConnection()) {
            Assumptions.assumeTrue(
                    VecDbSupport.resolveApiVersion(connection) == VecDbApiVersion.V23_26_3,
                    "Physical migration requires Oracle Database 23.26.3 or later");
        }
        VecDbTestOperations.dropVectorTable(TABLE_NAME);
        createStore(CreateOption.CREATE_OR_REPLACE);
    }

    /**
     * Verifies that all required DDL actions upgrade the earlier layout without replacing the table
     * or losing its vector, text, and metadata.
     */
    @Test
    void testMigratesEarlierLayoutAndPreservesStoredVector() throws SQLException {
        OracleVecDbEmbeddingStore initialStore = createStore(CreateOption.CREATE_NONE);
        TextSegment segment = TextSegment.from("Preserved content", new Metadata().put("tenant", "acme"));
        Embedding embedding =
                VecDbTestOperations.embeddingModel().embed(segment).content();
        initialStore.addAll(List.of("preserved-id"), List.of(embedding), List.of(segment));
        long objectIdBeforeMigration = tableObjectId();

        changeToEarlierLayout();

        OracleVecDbEmbeddingStore migratedStore = createStore(CreateOption.CREATE_NONE);

        assertThat(inspectLayout().state()).isEqualTo(VecDbTableLayout.LayoutState.CURRENT);
        assertThat(tableObjectId()).isEqualTo(objectIdBeforeMigration);
        assertThat(VecDbTestOperations.listVectorIds(TABLE_NAME)).containsExactly("preserved-id");
        assertThat(migratedStore
                        .search(EmbeddingSearchRequest.builder()
                                .queryEmbedding(embedding)
                                .maxResults(1)
                                .build())
                        .matches())
                .singleElement()
                .satisfies(match -> {
                    assertThat(match.embeddingId()).isEqualTo("preserved-id");
                    assertThat(match.embedded().text()).isEqualTo("Preserved content");
                    assertThat(match.embedded().metadata().getString("tenant")).isEqualTo("acme");
                });
    }

    /** Verifies that a partially migrated table receives only the columns that are still missing. */
    @Test
    void testCompletesPartiallyMigratedLayout() throws SQLException {
        execute("ALTER TABLE " + TABLE_NAME + " RENAME COLUMN CONTENT_METADATA TO METADATA");
        execute("ALTER TABLE " + TABLE_NAME + " DROP COLUMN CONTENT");
        execute("ALTER TABLE " + TABLE_NAME + " DROP COLUMN CONTENT_TYPE");
        assertThat(inspectLayout().state()).isEqualTo(VecDbTableLayout.LayoutState.PARTIALLY_MIGRATED);

        createStore(CreateOption.CREATE_NONE);

        VecDbTableLayout layout = inspectLayout();
        assertThat(layout.state()).isEqualTo(VecDbTableLayout.LayoutState.CURRENT);
        assertThat(layout.hasColumn(VecDbTableLayout.SPARSE_VECTOR)).isTrue();
        assertThat(layout.hasColumn(VecDbTableLayout.CONTENT)).isTrue();
        assertThat(layout.hasColumn(VecDbTableLayout.CONTENT_TYPE)).isTrue();
    }

    @AfterEach
    void dropVectorTable() throws SQLException {
        VecDbTestOperations.dropVectorTable(TABLE_NAME);
    }

    private static OracleVecDbEmbeddingStore createStore(CreateOption createOption) {
        return OracleVecDbEmbeddingStore.builder()
                .dataSource(VecDbTestOperations.dataSource())
                .embeddingTable(TABLE_NAME, createOption)
                .distanceMetric(VecDbDistanceMetric.COSINE)
                .build();
    }

    private static void changeToEarlierLayout() throws SQLException {
        execute("ALTER TABLE " + TABLE_NAME + " RENAME COLUMN CONTENT_METADATA TO METADATA");
        execute("ALTER TABLE " + TABLE_NAME + " DROP COLUMN SPARSE_VECTOR");
        execute("ALTER TABLE " + TABLE_NAME + " DROP COLUMN CONTENT");
        execute("ALTER TABLE " + TABLE_NAME + " DROP COLUMN CONTENT_TYPE");
        assertThat(inspectLayout().state()).isEqualTo(VecDbTableLayout.LayoutState.LEGACY);
    }

    private static VecDbTableLayout inspectLayout() throws SQLException {
        try (Connection connection = VecDbTestOperations.dataSource().getConnection()) {
            return new VecDbJdbcQueryExecutor().inspectTableLayout(connection, TABLE_NAME);
        }
    }

    private static long tableObjectId() throws SQLException {
        try (Connection connection = VecDbTestOperations.dataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT OBJECT_ID FROM USER_OBJECTS WHERE OBJECT_NAME = ? AND OBJECT_TYPE = 'TABLE'")) {
            statement.setString(1, TABLE_NAME);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getLong(1);
            }
        }
    }

    private static void execute(String sql) throws SQLException {
        try (Connection connection = VecDbTestOperations.dataSource().getConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }
}
