package dev.langchain4j.store.embedding.pgvector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.AdditionalAnswers;

class ColumnsMetadataHandlerTest {

    @Test
    void createMetadataIndexes() throws SQLException {
        Statement statement = mock(Statement.class);
        List<String> sqlStatementQueries = new ArrayList<>();
        when(statement.executeUpdate(anyString()))
                .thenAnswer(AdditionalAnswers.answerVoid(q -> sqlStatementQueries.add((String) q)));

        MetadataStorageConfig metadataStorageConfig = DefaultMetadataStorageConfig.builder()
                .storageMode(MetadataStorageMode.COLUMN_PER_KEY)
                .columnDefinitions(Collections.singletonList("age integer"))
                .indexes(Collections.singletonList("age"))
                .indexType("BTREE")
                .build();
        ColumnsMetadataHandler columnsMetadataHandler = new ColumnsMetadataHandler(metadataStorageConfig);

        columnsMetadataHandler.createMetadataIndexes(statement, "embeddings");

        assertThat(sqlStatementQueries)
                .containsExactly("create index if not exists embeddings_age on embeddings USING BTREE ( age )");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "btree", "hash", "gin", "gist", "brin", "spgist", "BTREE", "HASH", "GIN", "GIST", "BRIN", "SPGIST"
            })
    void createMetadataIndexes_allowsSupportedIndexTypes(String indexType) throws SQLException {
        Statement statement = mock(Statement.class);
        List<String> sqlStatementQueries = new ArrayList<>();
        when(statement.executeUpdate(anyString()))
                .thenAnswer(AdditionalAnswers.answerVoid(q -> sqlStatementQueries.add((String) q)));

        MetadataStorageConfig metadataStorageConfig = DefaultMetadataStorageConfig.builder()
                .storageMode(MetadataStorageMode.COLUMN_PER_KEY)
                .columnDefinitions(Collections.singletonList("age integer"))
                .indexes(Collections.singletonList("age"))
                .indexType(indexType)
                .build();
        ColumnsMetadataHandler columnsMetadataHandler = new ColumnsMetadataHandler(metadataStorageConfig);

        columnsMetadataHandler.createMetadataIndexes(statement, "embeddings");

        assertThat(sqlStatementQueries).hasSize(1);
        assertThat(sqlStatementQueries.get(0)).contains("USING " + indexType + " ");
    }

    @ParameterizedTest
    @ValueSource(strings = {"BTREE; drop table users;--", "unknown", "", " "})
    void createMetadataIndexes_rejectsUnsupportedIndexType(String indexType) {
        Statement statement = mock(Statement.class);

        MetadataStorageConfig metadataStorageConfig = DefaultMetadataStorageConfig.builder()
                .storageMode(MetadataStorageMode.COLUMN_PER_KEY)
                .columnDefinitions(Collections.singletonList("age integer"))
                .indexes(Collections.singletonList("age"))
                .indexType(indexType)
                .build();
        ColumnsMetadataHandler columnsMetadataHandler = new ColumnsMetadataHandler(metadataStorageConfig);

        assertThatThrownBy(() -> columnsMetadataHandler.createMetadataIndexes(statement, "embeddings"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("index type");
        verifyNoInteractions(statement);
    }
}
