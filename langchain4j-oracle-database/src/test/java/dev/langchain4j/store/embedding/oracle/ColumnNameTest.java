package dev.langchain4j.store.embedding.oracle;

import dev.langchain4j.store.embedding.EmbeddingSearchRequest;

import static org.junit.jupiter.api.Assertions.*;

import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotEqualTo;
import dev.langchain4j.store.embedding.filter.logical.And;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.hamcrest.core.Is;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Verifies {@link OracleEmbeddingStore.Builder} methods which configure the names of columns.
 */
public class ColumnNameTest {

    private static final String TABLE_NAME = "COLUMN_NAME_TEST";

    private static final String ID_COLUMN = "TEST_ID";
    private static final String EMBEDDING_COLUMN = "TEST_EMBEDDING";
    private static final String TEXT_COLUMN = "TEST_TEXT";
    private static final String METADATA_COLUMN = "TEST_METADATA";

    private static final OracleEmbeddingStore.Builder BUILDER =
            OracleEmbeddingStore.builder()
                    .dataSource(CommonTestOperations.getDataSource())
                    .tableName(TABLE_NAME)
                    .idColumn(ID_COLUMN)
                    .embeddingColumn(EMBEDDING_COLUMN)
                    .textColumn(TEXT_COLUMN)
                    .metadataColumn(METADATA_COLUMN);
    /**
     *  Verifies that {@link dev.langchain4j.store.embedding.oracle.OracleEmbeddingStore.Builder#build()} creates a
     *  table with the correct column names
     */
    @Test
    public void testBuild() throws SQLException {
        // The call to build() should create a table with the configured names
        BUILDER.build();

        Set<String> actualNames = new HashSet<>();

        // Query the database to get the column names
        try (Connection connection = CommonTestOperations.getDataSource().getConnection();
             ResultSet resultSet = connection.getMetaData().getColumns(null, connection.getSchema(), TABLE_NAME, "%")) {
            while (resultSet.next()) {
                assertEquals(TABLE_NAME, resultSet.getString("TABLE_NAME"));
                actualNames.add(resultSet.getString("COLUMN_NAME"));
            }
        }

        // Verify the names:
        Set<String> expectedNames =
                Stream.of(ID_COLUMN, EMBEDDING_COLUMN, TEXT_COLUMN, METADATA_COLUMN)
                        .collect(Collectors.toSet());
        assertEquals(expectedNames, actualNames);
    }

    /** Verifies that add, search, and remove methods use the correct column names */
    @Test
    public void testAddSearchAndRemove() throws SQLException {
        OracleEmbeddingStore embeddingStore = BUILDER.build();

        // Call add methods to make sure the SQL includes the correct names
        Set<TestData> expectedData = new HashSet<>();
        expectedData.add(TestData.add(embeddingStore));
        expectedData.add(TestData.addWithId(embeddingStore));
        expectedData.add(TestData.addWithTextSegment(embeddingStore));
        expectedData.addAll(TestData.addAll(embeddingStore));
        expectedData.addAll(TestData.addAllWithTextSegment(embeddingStore));

        // Request all embeddings
        EmbeddingSearchRequest requestAll = EmbeddingSearchRequest.builder()
                .queryEmbedding(TestData.randomEmbedding())
                .maxResults(expectedData.size())
                .minScore(0.0)
                .build();
        Set<TestData> actualData = embeddingStore.search(requestAll)
                .matches()
                .stream()
                .map(TestData::new)
                .collect(Collectors.toSet());

        assertEquals(expectedData, actualData);

        // Remove no embeddings
        embeddingStore.removeAll(new And(new IsEqualTo("x", 0), new IsNotEqualTo("x", 0)));

        // Remove all embeddings
        embeddingStore.removeAll(
                expectedData.stream()
                    .map(testData -> testData.id)
                    .collect(Collectors.toList()));

        assertTrue(embeddingStore.search(requestAll).matches().isEmpty());

    }

    @AfterEach
    public void cleanUp() throws SQLException {
        CommonTestOperations.dropTable(TABLE_NAME);
    }

}
