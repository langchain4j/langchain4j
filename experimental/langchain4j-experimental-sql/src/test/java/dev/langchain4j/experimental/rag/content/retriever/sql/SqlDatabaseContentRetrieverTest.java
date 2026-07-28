package dev.langchain4j.experimental.rag.content.retriever.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

import dev.langchain4j.model.chat.ChatModel;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class SqlDatabaseContentRetrieverTest {

    private final SqlDatabaseContentRetriever retriever = SqlDatabaseContentRetriever.builder()
            .dataSource(mock(DataSource.class))
            .sqlDialect("PostgreSQL")
            .databaseStructure("test")
            .chatModel(mock(ChatModel.class))
            .build();

    @Test
    void clean_should_strip_content_from_closed_sql_fence() {
        assertThat(retriever.clean("```sql\nSELECT * FROM customer\n```")).isEqualTo("\nSELECT * FROM customer\n");
    }

    @Test
    void clean_should_strip_content_from_closed_plain_fence() {
        assertThat(retriever.clean("```\nSELECT * FROM customer\n```")).isEqualTo("\nSELECT * FROM customer\n");
    }

    @Test
    void clean_should_not_throw_when_sql_fence_is_not_closed() {
        assertThatCode(() -> retriever.clean("```sql\nSELECT * FROM customer")).doesNotThrowAnyException();
        assertThat(retriever.clean("```sql\nSELECT * FROM customer")).isEqualTo("\nSELECT * FROM customer");
    }

    @Test
    void clean_should_not_throw_when_plain_fence_is_not_closed() {
        assertThatCode(() -> retriever.clean("```\nSELECT * FROM customer")).doesNotThrowAnyException();
        assertThat(retriever.clean("```\nSELECT * FROM customer")).isEqualTo("\nSELECT * FROM customer");
    }

    @Test
    void clean_should_return_plain_text_unchanged() {
        assertThat(retriever.clean("SELECT * FROM customer")).isEqualTo("SELECT * FROM customer");
    }
}
