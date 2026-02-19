package dev.langchain4j.store.embedding.filter.builder.sql;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.*;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.filter.parser.sql.SqlFilterParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LanguageModelSqlFilterBuilderTest {

    TableDefinition tableDefinition =
            TableDefinition.builder().name("table").addColumn("column", "type").build();

    SqlFilterParser sqlFilterParser = spy(new SqlFilterParser());

    @ParameterizedTest
    @ValueSource(
            strings = {
                "SELECT * FROM table WHERE id = 1",
                "SELECT * FROM table WHERE id = 1;",
                " SELECT * FROM table WHERE id = 1 ",
                " SELECT * FROM table WHERE id = 1; ",
                "SELECT * FROM table WHERE id = 1\n",
                "SELECT * FROM table WHERE id = 1;\n"
            })
    void should_parse_valid_SQL(String validSql) {

        // given
        ChatModel chatModel = ChatModelMock.thatAlwaysResponds(validSql);

        LanguageModelSqlFilterBuilder sqlFilterBuilder = LanguageModelSqlFilterBuilder.builder()
                .chatModel(chatModel)
                .tableDefinition(tableDefinition)
                .sqlFilterParser(sqlFilterParser)
                .build();

        Query query = Query.from("does not matter");

        // when
        sqlFilterBuilder.build(query);

        // then
        verify(sqlFilterParser).parse(validSql.trim());
        verifyNoMoreInteractions(sqlFilterParser);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "```sql\nSELECT * FROM table WHERE id = 1\n```",
                "```sql\nSELECT * FROM table WHERE id = 1",
                "```\nSELECT * FROM table WHERE id = 1\n```",
                "```\nSELECT * FROM table WHERE id = 1",
                "SELECT * FROM table WHERE id = 1\n```",
                "Of course, here is your SQL query ```sql\nSELECT * FROM table WHERE id = 1",
                "Of course, here is your SQL query ```sql\nSELECT * FROM table WHERE id = 1\n```\nmore text",
                "Of course, here is your SQL query ```SELECT * FROM table WHERE id = 1",
                "Of course, here is your SQL query ```SELECT * FROM table WHERE id = 1\n```\nmore text",
                "Of course, here is your SQL query SELECT * FROM table WHERE id = 1",
                "Of course, here is your SQL query SELECT * FROM table WHERE id = 1\n more text",
                "Of course, here is your SQL query\nSELECT * FROM table WHERE id = 1",
                "Of course, here is your SQL query\nSELECT * FROM table WHERE id = 1\nmore text",
                "Of course, here is your SELECT query SELECT * FROM table WHERE id = 1",
                "Of course, here is your SELECT query SELECT * FROM table WHERE id = 1\n more text",
                "Of course, here is your SELECT query\nSELECT * FROM table WHERE id = 1"
            })
    void should_fail_to_parse_then_extract_valid_SQL(String dirtySql) {

        // given
        ChatModel chatModel = ChatModelMock.thatAlwaysResponds(dirtySql);

        LanguageModelSqlFilterBuilder sqlFilterBuilder = LanguageModelSqlFilterBuilder.builder()
                .chatModel(chatModel)
                .tableDefinition(tableDefinition)
                .sqlFilterParser(sqlFilterParser)
                .build();

        Query query = Query.from("does not matter");

        // when
        sqlFilterBuilder.build(query);

        // then
        verify(sqlFilterParser).parse(dirtySql);
        verify(sqlFilterParser).parse("SELECT * FROM table WHERE id = 1");
        verifyNoMoreInteractions(sqlFilterParser);
    }

    @Test
    void should_handle_null_chat_model() {
        assertThatThrownBy(() -> LanguageModelSqlFilterBuilder.builder()
                        .chatModel(null)
                        .tableDefinition(tableDefinition)
                        .sqlFilterParser(sqlFilterParser)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chatModel");
    }

    @Test
    void should_handle_null_table_definition() {
        ChatModel chatModel = ChatModelMock.thatAlwaysResponds("SELECT * FROM table");

        assertThatThrownBy(() -> LanguageModelSqlFilterBuilder.builder()
                        .chatModel(chatModel)
                        .tableDefinition(null)
                        .sqlFilterParser(sqlFilterParser)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tableDefinition");
    }

    @Test
    void should_handle_table_definition_with_multiple_columns() {
        TableDefinition complexTable = TableDefinition.builder()
                .name("users")
                .addColumn("id", "INTEGER")
                .addColumn("name", "VARCHAR")
                .build();

        ChatModel chatModel = ChatModelMock.thatAlwaysResponds("SELECT * FROM users WHERE id = 1");

        LanguageModelSqlFilterBuilder sqlFilterBuilder = LanguageModelSqlFilterBuilder.builder()
                .chatModel(chatModel)
                .tableDefinition(complexTable)
                .sqlFilterParser(sqlFilterParser)
                .build();

        Query query = Query.from("test");
        sqlFilterBuilder.build(query);

        verify(sqlFilterParser).parse("SELECT * FROM users WHERE id = 1");
    }

    @Test
    void should_use_retry_strategy_when_configured() {
        // Given a chat model that first returns invalid SQL, then valid SQL
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.chat(any(dev.langchain4j.data.message.UserMessage.class)))
                .thenReturn(new dev.langchain4j.model.chat.response.ChatResponse.Builder()
                        .aiMessage(new dev.langchain4j.data.message.AiMessage("INVALID SQL"))
                        .build())
                .thenReturn(new dev.langchain4j.model.chat.response.ChatResponse.Builder()
                        .aiMessage(new dev.langchain4j.data.message.AiMessage("SELECT * FROM table WHERE id = 1"))
                        .build());

        LanguageModelSqlFilterBuilder sqlFilterBuilder = LanguageModelSqlFilterBuilder.builder()
                .chatModel(chatModel)
                .tableDefinition(tableDefinition)
                .retryStrategy(RetryStrategy.RETRY_WITH_SIMPLE_PROMPT)
                .maxRetries(2)
                .build();

        Query query = Query.from("test");
        sqlFilterBuilder.build(query);

        // Verify that the chat model was called twice (initial + retry)
        verify(chatModel, times(2)).chat(any(dev.langchain4j.data.message.UserMessage.class));
    }

    @Test
    void should_use_default_retry_strategy_none() {
        ChatModel chatModel = ChatModelMock.thatAlwaysResponds("INVALID SQL");

        LanguageModelSqlFilterBuilder sqlFilterBuilder = LanguageModelSqlFilterBuilder.builder()
                .chatModel(chatModel)
                .tableDefinition(tableDefinition)
                .build();

        Query query = Query.from("test");
        var result = sqlFilterBuilder.build(query);

        // With NONE strategy, should return null when parsing fails
        org.assertj.core.api.Assertions.assertThat(result).isNull();
    }

    @Test
    void should_extract_sql_from_complex_response() {
        String complexResponse = "Sure! Here's the SQL query you requested:\n\n" + "```sql\n"
                + "SELECT * FROM users WHERE name = 'John'\n"
                + "```\n\n"
                + "This query will find all users named John.";

        ChatModel chatModel = ChatModelMock.thatAlwaysResponds(complexResponse);

        LanguageModelSqlFilterBuilder sqlFilterBuilder = LanguageModelSqlFilterBuilder.builder()
                .chatModel(chatModel)
                .tableDefinition(tableDefinition)
                .sqlFilterParser(sqlFilterParser)
                .build();

        Query query = Query.from("test");
        sqlFilterBuilder.build(query);

        // Should extract and parse the SQL from within the code block
        verify(sqlFilterParser, atLeastOnce()).parse(contains("SELECT"));
    }
}
