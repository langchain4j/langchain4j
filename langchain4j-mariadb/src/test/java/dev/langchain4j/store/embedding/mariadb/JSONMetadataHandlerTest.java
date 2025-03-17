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
}
