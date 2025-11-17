package dev.langchain4j.store.embedding.mariadb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.Mockito.*;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;

class JSONMetadataHandlerTest {

    @Test
    void createSimpleMetadataIndexes() throws SQLException {
        Statement statement = mock(Statement.class);
        List<String> sqlStatementQueries = new ArrayList<>();
        when(statement.executeUpdate(anyString()))
                .thenAnswer(AdditionalAnswers.answerVoid(q -> sqlStatementQueries.add((String) q)));

        MetadataStorageConfig metadataStorageConfig = DefaultMetadataStorageConfig.builder()
                .storageMode(MetadataStorageMode.COMBINED_JSON)
                .columnDefinitions(Collections.singletonList("metadata JSON"))
                .indexes(Collections.singletonList("country"))
                .build();
        JSONMetadataHandler jsonMetadataHandler =
                new JSONMetadataHandler(metadataStorageConfig, Collections.emptyList());
        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> jsonMetadataHandler.createMetadataIndexes(statement, "embeddings"));

        assertThat(sqlStatementQueries).isEmpty();
    }

    @Test
    void createSimpleMetadataIndexes_json_path_ops() throws SQLException {
        Statement statement = mock(Statement.class);
        List<String> sqlStatementQueries = new ArrayList<>();
        when(statement.executeUpdate(anyString()))
                .thenAnswer(AdditionalAnswers.answerVoid(q -> sqlStatementQueries.add((String) q)));

        MetadataStorageConfig metadataStorageConfig = DefaultMetadataStorageConfig.builder()
                .storageMode(MetadataStorageMode.COMBINED_JSON)
                .columnDefinitions(Collections.singletonList("metadata JSON"))
                .indexes(Collections.singletonList("json_path_ops"))
                .build();
        JSONMetadataHandler jsonMetadataHandler =
                new JSONMetadataHandler(metadataStorageConfig, Collections.emptyList());
        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> jsonMetadataHandler.createMetadataIndexes(statement, "embeddings"));

        assertThat(sqlStatementQueries).isEmpty();
    }

    @Test
    void createJSONNodeMetadataIndexes() throws SQLException {
        Statement statement = mock(Statement.class);
        List<String> sqlStatementQueries = new ArrayList<>();
        when(statement.executeUpdate(anyString()))
                .thenAnswer(AdditionalAnswers.answerVoid(q -> sqlStatementQueries.add((String) q)));

        MetadataStorageConfig metadataStorageConfig = DefaultMetadataStorageConfig.builder()
                .storageMode(MetadataStorageMode.COMBINED_JSON)
                .columnDefinitions(Collections.singletonList("metadata JSON"))
                .indexes(Arrays.asList("key1", "key2"))
                .build();
        JSONMetadataHandler jsonMetadataHandler =
                new JSONMetadataHandler(metadataStorageConfig, Collections.emptyList());
        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> jsonMetadataHandler.createMetadataIndexes(statement, "embeddings"));

        assertThat(sqlStatementQueries).isEmpty();
    }

    @Test
    void testEmptyIndexesList() throws SQLException {
        Statement statement = mock(Statement.class);

        MetadataStorageConfig metadataStorageConfig = DefaultMetadataStorageConfig.builder()
                .storageMode(MetadataStorageMode.COMBINED_JSON)
                .columnDefinitions(Collections.singletonList("metadata JSON"))
                .indexes(Collections.emptyList())
                .build();

        JSONMetadataHandler jsonMetadataHandler =
                new JSONMetadataHandler(metadataStorageConfig, Collections.emptyList());

        // Should not throw exception with empty indexes
        jsonMetadataHandler.createMetadataIndexes(statement, "embeddings");

        verify(statement, never()).executeUpdate(anyString());
    }

    @Test
    void testNullIndexesList() throws SQLException {
        Statement statement = mock(Statement.class);

        MetadataStorageConfig metadataStorageConfig = DefaultMetadataStorageConfig.builder()
                .storageMode(MetadataStorageMode.COMBINED_JSON)
                .columnDefinitions(Collections.singletonList("metadata JSON"))
                .indexes(null)
                .build();

        JSONMetadataHandler jsonMetadataHandler =
                new JSONMetadataHandler(metadataStorageConfig, Collections.emptyList());

        // Should handle null indexes gracefully
        jsonMetadataHandler.createMetadataIndexes(statement, "embeddings");

        verify(statement, never()).executeUpdate(anyString());
    }

    @Test
    void testMultipleIndexesThrowsException() {
        Statement statement = mock(Statement.class);

        MetadataStorageConfig metadataStorageConfig = DefaultMetadataStorageConfig.builder()
                .storageMode(MetadataStorageMode.COMBINED_JSON)
                .columnDefinitions(Collections.singletonList("metadata JSON"))
                .indexes(Arrays.asList("index1", "index2", "index3"))
                .build();

        JSONMetadataHandler jsonMetadataHandler =
                new JSONMetadataHandler(metadataStorageConfig, Collections.emptyList());

        // JSON metadata doesn't support indexes
        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> jsonMetadataHandler.createMetadataIndexes(statement, "embeddings"))
                .withMessageContaining("Indexes are actually not allowed for JSON metadata");
    }

    @Test
    void testNullTableName() {
        Statement statement = mock(Statement.class);

        MetadataStorageConfig metadataStorageConfig = DefaultMetadataStorageConfig.builder()
                .storageMode(MetadataStorageMode.COMBINED_JSON)
                .columnDefinitions(Collections.singletonList("metadata JSON"))
                .indexes(Collections.singletonList("field1"))
                .build();

        JSONMetadataHandler jsonMetadataHandler =
                new JSONMetadataHandler(metadataStorageConfig, Collections.emptyList());

        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> jsonMetadataHandler.createMetadataIndexes(statement, null));
    }

    @Test
    void testEmptyTableName() {
        Statement statement = mock(Statement.class);

        MetadataStorageConfig metadataStorageConfig = DefaultMetadataStorageConfig.builder()
                .storageMode(MetadataStorageMode.COMBINED_JSON)
                .columnDefinitions(Collections.singletonList("metadata JSON"))
                .indexes(Collections.singletonList("field1"))
                .build();

        JSONMetadataHandler jsonMetadataHandler =
                new JSONMetadataHandler(metadataStorageConfig, Collections.emptyList());

        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> jsonMetadataHandler.createMetadataIndexes(statement, ""));
    }

    @Test
    void testConstructorWithEmptyMetadataKeys() {
        MetadataStorageConfig metadataStorageConfig = DefaultMetadataStorageConfig.builder()
                .storageMode(MetadataStorageMode.COMBINED_JSON)
                .columnDefinitions(Collections.singletonList("metadata JSON"))
                .build();

        // Should handle empty metadataKeys in constructor
        JSONMetadataHandler jsonMetadataHandler =
                new JSONMetadataHandler(metadataStorageConfig, Collections.emptyList());

        assertThat(jsonMetadataHandler).isNotNull();
    }

    @Test
    void testConstructorWithMetadataKeys() {
        MetadataStorageConfig metadataStorageConfig = DefaultMetadataStorageConfig.builder()
                .storageMode(MetadataStorageMode.COMBINED_JSON)
                .columnDefinitions(Collections.singletonList("metadata JSON"))
                .build();

        List<String> metadataKeys = Arrays.asList("key1", "key2", "key3");

        JSONMetadataHandler jsonMetadataHandler = new JSONMetadataHandler(metadataStorageConfig, metadataKeys);

        assertThat(jsonMetadataHandler).isNotNull();
    }

    @Test
    void testStatementExecutionWithValidConfig() throws SQLException {
        Statement statement = mock(Statement.class);

        MetadataStorageConfig metadataStorageConfig = DefaultMetadataStorageConfig.builder()
                .storageMode(MetadataStorageMode.COMBINED_JSON)
                .columnDefinitions(Collections.singletonList("metadata JSON"))
                .indexes(Collections.emptyList())
                .build();

        JSONMetadataHandler jsonMetadataHandler =
                new JSONMetadataHandler(metadataStorageConfig, Arrays.asList("field1", "field2"));

        jsonMetadataHandler.createMetadataIndexes(statement, "test_table");

        // Verify no SQL execution for JSON metadata
        verify(statement, never()).executeUpdate(anyString());
    }

    @Test
    void testIndexesNotAllowedMessage() {
        Statement statement = mock(Statement.class);

        MetadataStorageConfig metadataStorageConfig = DefaultMetadataStorageConfig.builder()
                .storageMode(MetadataStorageMode.COMBINED_JSON)
                .columnDefinitions(Collections.singletonList("metadata JSON"))
                .indexes(Collections.singletonList("some_index"))
                .build();

        JSONMetadataHandler jsonMetadataHandler =
                new JSONMetadataHandler(metadataStorageConfig, Collections.emptyList());

        // Verify the specific error message for JSON indexes
        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> jsonMetadataHandler.createMetadataIndexes(statement, "embeddings"))
                .withMessage("Indexes are actually not allowed for JSON metadata");
    }
}
