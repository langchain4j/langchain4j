package dev.langchain4j.store.embedding.pgvector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;

class ColumnsMetadataHandlerTest {

    @Test
    void createSingleColumnIndexes() throws SQLException {
        Statement statement = mock(Statement.class);
        List<String> sqlStatementQueries = new ArrayList<>();
        when(statement.executeUpdate(anyString()))
                .thenAnswer(AdditionalAnswers.answerVoid(q -> sqlStatementQueries.add((String) q)));

        MetadataStorageConfig config = DefaultMetadataStorageConfig.builder()
                .storageMode(MetadataStorageMode.COLUMN_PER_KEY)
                .columnDefinitions(Arrays.asList("query_id uuid null", "type varchar null"))
                .indexes(Arrays.asList("query_id", "type"))
                .build();

        ColumnsMetadataHandler handler = new ColumnsMetadataHandler(config);
        handler.createMetadataIndexes(statement, "ai_category");

        assertThat(sqlStatementQueries).hasSize(2);
        assertThat(sqlStatementQueries.get(0))
                .isEqualTo("create index if not exists ai_category_query_id on ai_category  ( query_id )");
        assertThat(sqlStatementQueries.get(1))
                .isEqualTo("create index if not exists ai_category_type on ai_category  ( type )");
    }

    @Test
    void createCompoundIndex() throws SQLException {
        Statement statement = mock(Statement.class);
        List<String> sqlStatementQueries = new ArrayList<>();
        when(statement.executeUpdate(anyString()))
                .thenAnswer(AdditionalAnswers.answerVoid(q -> sqlStatementQueries.add((String) q)));

        MetadataStorageConfig config = DefaultMetadataStorageConfig.builder()
                .storageMode(MetadataStorageMode.COLUMN_PER_KEY)
                .columnDefinitions(Arrays.asList("query_id uuid null", "type varchar null", "version varchar null"))
                .compoundIndexes(Collections.singletonList(Arrays.asList("query_id", "type", "version")))
                .build();

        ColumnsMetadataHandler handler = new ColumnsMetadataHandler(config);
        handler.createMetadataIndexes(statement, "ai_category");

        assertThat(sqlStatementQueries).hasSize(1);
        assertThat(sqlStatementQueries.get(0))
                .isEqualTo(
                        "create index if not exists ai_category_query_id_type_version on ai_category  ( query_id, type, version )");
    }

    @Test
    void createMixedSingleAndCompoundIndexes() throws SQLException {
        Statement statement = mock(Statement.class);
        List<String> sqlStatementQueries = new ArrayList<>();
        when(statement.executeUpdate(anyString()))
                .thenAnswer(AdditionalAnswers.answerVoid(q -> sqlStatementQueries.add((String) q)));

        MetadataStorageConfig config = DefaultMetadataStorageConfig.builder()
                .storageMode(MetadataStorageMode.COLUMN_PER_KEY)
                .columnDefinitions(Arrays.asList("query_id uuid null", "type varchar null", "version varchar null"))
                .indexes(Collections.singletonList("version"))
                .compoundIndexes(Collections.singletonList(Arrays.asList("query_id", "type")))
                .build();

        ColumnsMetadataHandler handler = new ColumnsMetadataHandler(config);
        handler.createMetadataIndexes(statement, "ai_category");

        assertThat(sqlStatementQueries).hasSize(2);
        assertThat(sqlStatementQueries.get(0))
                .isEqualTo("create index if not exists ai_category_version on ai_category  ( version )");
        assertThat(sqlStatementQueries.get(1))
                .isEqualTo("create index if not exists ai_category_query_id_type on ai_category  ( query_id, type )");
    }
}
