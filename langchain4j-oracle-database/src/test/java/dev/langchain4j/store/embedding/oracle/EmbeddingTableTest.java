package dev.langchain4j.store.embedding.oracle;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotEqualTo;
import dev.langchain4j.store.embedding.filter.logical.And;
import oracle.jdbc.OracleType;
import org.junit.jupiter.api.Test;

import java.sql.*;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static dev.langchain4j.store.embedding.oracle.CommonTestOperations.dropTable;
import static dev.langchain4j.store.embedding.oracle.CommonTestOperations.getDataSource;
import static dev.langchain4j.store.embedding.oracle.CreateOption.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Verifies {@link OracleEmbeddingStore.Builder} methods which configure the names of columns.
 */
public class EmbeddingTableTest {

    /**
     *  Verifies that {@link dev.langchain4j.store.embedding.oracle.OracleEmbeddingStore.Builder#build()} creates a
     *  table with non-default names
     */
    @Test
    public void testAsciiNames() throws SQLException {
        String tableName = "TEST";
        String idColumn = "TEST_ID";
        String embeddingColumn = "TEST_EMBEDDING";
        String textColumn = "TEST_TEXT";
        String metadataColumn = "TEST_METADATA";
        try {
            // The call to build() should create a table with the configured names
            OracleEmbeddingStore embeddingStore =
                OracleEmbeddingStore.builder()
                        .dataSource(getDataSource())
                        .embeddingTable(EmbeddingTable.builder()
                                .createOption(CREATE_OR_REPLACE)
                                .name(tableName)
                                .idColumn(idColumn)
                                .embeddingColumn(embeddingColumn)
                                .textColumn(textColumn)
                                .metadataColumn(metadataColumn)
                                .build())
                        .build();

            assertColumnNamesEquals(tableName, idColumn, embeddingColumn, textColumn, metadataColumn);
            verifyAddSearchAndRemove(embeddingStore);
        }
        finally {
            CommonTestOperations.dropTable(tableName);
        }
    }

    /**
     *  Verifies that {@link dev.langchain4j.store.embedding.oracle.OracleEmbeddingStore.Builder#build()} creates a
     *  table with non-default names that include unicode characters.
     */
    @Test
    public void testUnicodeNames() throws SQLException {
        assumeTrue(CommonTestOperations.getCharacterSet().isUnicode());

        String tableName = "δεδομένα";
        String idColumn = "ідентичність";
        String embeddingColumn = "埋め込み";
        String textColumn = "טֶקסט";
        String metadataColumn = "البيانات الوصفية";
        try {
            // Oracle Database supports non-ascii identifiers wrapped in double quotes.
            OracleEmbeddingStore embeddingStore =
                OracleEmbeddingStore.builder()
                        .dataSource(getDataSource())
                        .embeddingTable(EmbeddingTable.builder()
                                .createOption(CREATE_OR_REPLACE)
                                .name("\"" + tableName +"\"")
                                .idColumn("\"" + idColumn + "\"")
                                .embeddingColumn("\"" + embeddingColumn + "\"")
                                .textColumn("\"" + textColumn + "\"")
                                .metadataColumn("\"" + metadataColumn + "\"")
                                .build())
                        .build();

            assertColumnNamesEquals(tableName, idColumn, embeddingColumn, textColumn, metadataColumn);
            verifyAddSearchAndRemove(embeddingStore);
        }
        finally {
            dropTable(tableName);
        }
    }

    /** Verifies the case where a table already exists, and the embedding store should not create it */
    @Test
    public void testNoCreation() throws SQLException {
        String tableName = "TEST_NO_CREATION";

        try {

            // Expect no table creation by default, and an error if table does not exist.
            try {
                OracleEmbeddingStore.builder()
                        .dataSource(getDataSource())
                        .embeddingTable(tableName)
                        .build()
                        .add(TestData.randomEmbedding());
            } catch (RuntimeException runtimeException) {
                assertDoesNotExistError(runtimeException);
            }

            // Expect no table creation for NO_CREATION, and an error if table does not exist.
            try {
                OracleEmbeddingStore.builder()
                        .dataSource(getDataSource())
                        .embeddingTable(EmbeddingTable.builder()
                                .createOption(CREATE_NONE)
                                .name(tableName)
                                .build())
                        .build()
                        .add(TestData.randomEmbedding());
            } catch (RuntimeException runtimeException) {
                assertDoesNotExistError(runtimeException);
            }

            createTable(tableName);
            verifyAddSearchAndRemove(OracleEmbeddingStore.builder()
                    .dataSource(getDataSource())
                    .embeddingTable(tableName)
                    .build());
        }
        finally {
            dropTable(tableName);
        }
    }

    /** Verifies the case where an existing table is reused */
    @Test
    public void testCreateIfNotExists() throws SQLException {
        String tableName = "TEST_CREATE_IF_NOT_EXISTS";
        dropTable(tableName); // to be sure

        try {

            // Expect the table to be created if it does not exist
            verifyAddSearchAndRemove(OracleEmbeddingStore.builder()
                    .dataSource(getDataSource())
                    .embeddingTable(tableName, CREATE_IF_NOT_EXISTS)
                    .build());

            // Set up the existing table to have just one row of data
            TestData testData =
                    new TestData(TestData.randomId(), TestData.randomEmbedding(), TextSegment.from("TEST"));
            try (Connection connection = getDataSource().getConnection();
                 Statement statement = connection.createStatement();
                 PreparedStatement insert = connection.prepareStatement(
                         "INSERT INTO " + tableName + "(id, embedding, text) VALUES (?, ?, ?)")) {

                statement.execute("DELETE FROM " + tableName);

                insert.setString(1, testData.id);
                insert.setObject(2, testData.embedding.vector(), OracleType.VECTOR);
                insert.setObject(3, testData.textSegment.text());
                insert.executeUpdate();
            }

            // Expect the existing table to be reused; A search of min score 0 should return 1 match.
            List<TestData> matches =
                OracleEmbeddingStore.builder()
                        .dataSource(getDataSource())
                        .embeddingTable(tableName, CREATE_IF_NOT_EXISTS)
                        .build()
                        .search(EmbeddingSearchRequest.builder()
                                .queryEmbedding(testData.embedding)
                                .minScore(0d)
                                .build())
                        .matches()
                        .stream()
                        .map(TestData::new)
                        .collect(Collectors.toList());
            assertEquals(Collections.singletonList(testData), matches);
        }
        finally {
            dropTable(tableName);
        }
    }

    /** Verifies the case where an existing table is dropped and replaced */
    @Test
    public void testCreateOrReplace() throws SQLException {
        String tableName = "TEST_CREATE_OR_REPLACE";
        dropTable(tableName); // to be sure

        try {

            // Expect the table to be created if it does not exist
            verifyAddSearchAndRemove(OracleEmbeddingStore.builder()
                    .dataSource(getDataSource())
                    .embeddingTable(tableName, CREATE_OR_REPLACE)
                    .build());

            // Set up the existing table to have at least one row of data
            Embedding embedding = TestData.randomEmbedding();
            try (Connection connection = getDataSource().getConnection();
                 Statement statement = connection.createStatement();
                 PreparedStatement insert = connection.prepareStatement(
                         "INSERT INTO " + tableName + "(id, embedding) VALUES (?, ?)")) {

                insert.setString(1, TestData.randomId());
                insert.setObject(2, embedding.vector(), OracleType.VECTOR);
                insert.executeUpdate();
            }

            // Expect the existing table to be dropped and replaced; A search of min score 0 should find no matches.
            List<TestData> matches =
                    OracleEmbeddingStore.builder()
                            .dataSource(getDataSource())
                            .embeddingTable(tableName, CREATE_OR_REPLACE)
                            .build()
                            .search(EmbeddingSearchRequest.builder()
                                    .queryEmbedding(embedding)
                                    .minScore(0d)
                                    .build())
                            .matches()
                            .stream()
                            .map(TestData::new)
                            .collect(Collectors.toList());
            assertEquals(Collections.emptyList(), matches);

        }
        finally {
            dropTable(tableName);
        }
    }

    /** Verifies that add, search, and remove methods use the correct column names */
    private void verifyAddSearchAndRemove(OracleEmbeddingStore embeddingStore) {

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

    /**
     * Verifies that a database table is created with the given names. Note that Oracle Database will convert
     * identifiers to upper case, unless they are enclosed in double quotes.
     */
    private static void assertColumnNamesEquals(
            String tableName,
            String idColumn, String embeddingColumn, String textColumn, String metadataColumn)
            throws SQLException {

        Set<String> actualNames = new HashSet<>();

        // Query the database to get the column names
        try (Connection connection = getDataSource().getConnection();
             ResultSet resultSet =
                     connection.getMetaData().getColumns(null, connection.getSchema(), tableName, "%")) {
            while (resultSet.next()) {
                assertEquals(tableName, resultSet.getString("TABLE_NAME"));
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

    /** Asserts that an exception is caused by a table which does not exist */
    private static void assertDoesNotExistError(RuntimeException runtimeException) {
        try {
            // Expect "ORA-00942: table or view does not exist"  if the table does not exist
            SQLException sqlException = assertInstanceOf(SQLException.class, runtimeException.getCause());
            assertEquals(942, sqlException.getErrorCode());
        }
        catch (AssertionError assertionError) {
            assertionError.addSuppressed(runtimeException);
            throw assertionError;
        }
    }

    /** Creates a table with the default column names */
    private static void createTable(String tableName) throws SQLException {
        try (Connection connection = getDataSource().getConnection();
             Statement statement = connection.createStatement()) {

            statement.execute("CREATE TABLE " + tableName +
                    "(id VARCHAR(36) PRIMARY KEY, embedding VECTOR, text CLOB, metadata JSON)");
        }
    }
}
