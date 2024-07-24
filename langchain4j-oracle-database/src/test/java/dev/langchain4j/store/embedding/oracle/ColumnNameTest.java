package dev.langchain4j.store.embedding.oracle;

import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotEqualTo;
import dev.langchain4j.store.embedding.filter.logical.And;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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
    public void testAscii() throws SQLException {
        // The call to build() should create a table with the configured names
        BUILDER.build();
        assertColumnNamesEquals(ID_COLUMN, EMBEDDING_COLUMN, TEXT_COLUMN, METADATA_COLUMN);
    }

    /** Verifies the use of non-ascii characters in a column name */
    @Test
    public void testUnicode() throws SQLException {
        assumeTrue(CommonTestOperations.getCharacterSet().isUnicode());

        String idColumn = "ідентичність";
        String embeddingColumn = "埋め込み";
        String textColumn = "טֶקסט";
        String metadataColumn = "البيانات الوصفية";

        // Oracle Database supports non-ascii identifiers wrapped in double quotes.
        OracleEmbeddingStore.builder()
                .dataSource(CommonTestOperations.getDataSource())
                .tableName(TABLE_NAME)
                .idColumn("\"" + idColumn + "\"")
                .embeddingColumn("\"" + embeddingColumn + "\"")
                .textColumn("\"" + textColumn + "\"")
                .metadataColumn("\"" + metadataColumn + "\"")
                .build();

        assertColumnNamesEquals(idColumn, embeddingColumn, textColumn, metadataColumn);
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

    private static void assertColumnNamesEquals(
            String idColumn, String embeddingColumn, String textColumn, String metadataColumn)
            throws SQLException {

        Set<String> actualNames = new HashSet<>();

        // Query the database to get the column names
        try (Connection connection = CommonTestOperations.getDataSource().getConnection();
             ResultSet resultSet =
                     connection.getMetaData().getColumns(null, connection.getSchema(), TABLE_NAME, "%")) {
            while (resultSet.next()) {
                assertEquals(TABLE_NAME, resultSet.getString("TABLE_NAME"));
                actualNames.add(resultSet.getString("COLUMN_NAME"));
            }
        }

        Set<String> expectedNames = new HashSet<>();
        expectedNames.add(idColumn);
        expectedNames.add(embeddingColumn);
        expectedNames.add(textColumn);
        expectedNames.add(metadataColumn);

        assertEquals(expectedNames, actualNames);
    }

    @AfterEach
    public void cleanUp() throws SQLException {
        CommonTestOperations.dropTable(TABLE_NAME);
    }

}
