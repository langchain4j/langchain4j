package dev.langchain4j.store.embedding.filter.builder.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.*;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.test.mock.MockChatModel;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.parser.sql.SqlFilterParser;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

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
        ChatModel chatModel = MockChatModel.thatResponds(validSql);

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
        ChatModel chatModel = MockChatModel.thatResponds(dirtySql);

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
    void should_build_filter_from_long_chain_of_conditions() {

        // given
        String sql = "SELECT * FROM table WHERE column = 0" + " AND column = 1".repeat(50_000);
        ChatModel chatModel = MockChatModel.thatResponds(sql);

        SqlFilterParser sqlFilterParser = spy(SqlFilterParser.builder()
                .maxConditions(Integer.MAX_VALUE)
                .parseTimeout(Duration.ofMinutes(1))
                .build());

        LanguageModelSqlFilterBuilder sqlFilterBuilder = LanguageModelSqlFilterBuilder.builder()
                .chatModel(chatModel)
                .tableDefinition(tableDefinition)
                .sqlFilterParser(sqlFilterParser)
                .build();

        // when
        Filter filter = sqlFilterBuilder.build(Query.from("does not matter"));

        // then
        assertThat(filter.getClass()).isEqualTo(And.class);
        verify(sqlFilterParser).parse(sql);
        verifyNoMoreInteractions(sqlFilterParser);
    }

    static Stream<Arguments> should_return_no_filter_when_SQL_is_over_parser_limits() {
        return Stream.of(
                Arguments.of(
                        "SELECT * FROM table WHERE column = 0" + " AND column = 1".repeat(100),
                        "SqlFilterParser.builder().maxConditions(...)"),
                Arguments.of(
                        "SELECT * FROM table WHERE " + "(".repeat(51) + "column = 1" + ")".repeat(51),
                        "SqlFilterParser.builder().maxNestingDepth(...)"));
    }

    @ParameterizedTest
    @MethodSource
    void should_return_no_filter_when_SQL_is_over_parser_limits(String sql, String builderOption) {

        // given
        ChatModel chatModel = MockChatModel.thatResponds(sql);

        LanguageModelSqlFilterBuilder sqlFilterBuilder = spy(LanguageModelSqlFilterBuilder.builder()
                .chatModel(chatModel)
                .tableDefinition(tableDefinition)
                .sqlFilterParser(sqlFilterParser)
                .build());

        Query query = Query.from("does not matter");

        // when
        Filter filter = sqlFilterBuilder.build(query);

        // then
        assertThat(filter).isNull();

        ArgumentCaptor<Exception> parsingError = ArgumentCaptor.forClass(Exception.class);
        verify(sqlFilterBuilder).fallback(eq(query), eq(sql), eq(sql), parsingError.capture());
        assertThat(parsingError.getValue())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("more than the maximum of")
                .hasMessageEndingWith("Increase it with " + builderOption);

        verify(sqlFilterParser).parse(sql); // not parsed again, as the extracted SQL is the same
        verifyNoMoreInteractions(sqlFilterParser);
    }

    @Test
    void should_return_no_filter_when_SQL_has_long_unsupported_expression() {

        // given
        String sql = "SELECT * FROM table WHERE column = 1" + " XOR column = 1".repeat(20_000);
        ChatModel chatModel = MockChatModel.thatResponds(sql);

        LanguageModelSqlFilterBuilder sqlFilterBuilder = LanguageModelSqlFilterBuilder.builder()
                .chatModel(chatModel)
                .tableDefinition(tableDefinition)
                .sqlFilterParser(sqlFilterParser)
                .build();

        // when
        Filter filter = sqlFilterBuilder.build(Query.from("does not matter"));

        // then
        assertThat(filter).isNull();
        verify(sqlFilterParser).parse(sql);
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
        ChatModel chatModel = MockChatModel.thatResponds("SELECT * FROM table");

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

        ChatModel chatModel = MockChatModel.thatResponds("SELECT * FROM users WHERE id = 1");

        LanguageModelSqlFilterBuilder sqlFilterBuilder = LanguageModelSqlFilterBuilder.builder()
                .chatModel(chatModel)
                .tableDefinition(complexTable)
                .sqlFilterParser(sqlFilterParser)
                .build();

        Query query = Query.from("test");
        sqlFilterBuilder.build(query);

        verify(sqlFilterParser).parse("SELECT * FROM users WHERE id = 1");
    }
}
