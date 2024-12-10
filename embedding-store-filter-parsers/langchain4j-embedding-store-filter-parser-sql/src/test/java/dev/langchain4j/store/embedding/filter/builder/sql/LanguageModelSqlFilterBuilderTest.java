package dev.langchain4j.store.embedding.filter.builder.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.parser.sql.SqlFilterParser;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LanguageModelSqlFilterBuilderTest {

    TableDefinition tableDefinition =
            TableDefinition.builder().name("table").addColumn("column", "type").build();

    @Mock
    SqlFilterParser sqlFilterParser;

    @Mock
    Filter filter;

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
        ChatLanguageModel chatLanguageModel = ChatModelMock.thatAlwaysResponds(validSql);

        LanguageModelSqlFilterBuilder sqlFilterBuilder = LanguageModelSqlFilterBuilder.builder()
                .chatLanguageModel(chatLanguageModel)
                .tableDefinition(tableDefinition)
                .sqlFilterParser(sqlFilterParser)
                .build();

        Query query = Query.from("does not matter");

        when(sqlFilterParser.parse(validSql.trim())).thenReturn(filter);

        // when
        var result = sqlFilterBuilder.build(query);

        // then
        assertThat(result).isSameAs(filter);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "```sql\nSELECT * FROM table WHERE id = 1\n```",
                "```sql\nSELECT * FROM table WHERE id = 1",
                "```\nSELECT * FROM table WHERE id = 1\n```",
                "```\nSELECT * FROM table WHERE id = 1",
                " SELECT * FROM table WHERE id = 1\n```",
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
        ChatLanguageModel chatLanguageModel = ChatModelMock.thatAlwaysResponds(dirtySql);

        LanguageModelSqlFilterBuilder sqlFilterBuilder = LanguageModelSqlFilterBuilder.builder()
                .chatLanguageModel(chatLanguageModel)
                .tableDefinition(tableDefinition)
                .sqlFilterParser(sqlFilterParser)
                .build();

        Query query = Query.from("does not matter");

        when(sqlFilterParser.parse(dirtySql.trim())).thenThrow(new RuntimeException("Invalid SQL"));
        when(sqlFilterParser.parse("SELECT * FROM table WHERE id = 1")).thenReturn(filter);

        // when
        var result = sqlFilterBuilder.build(query);

        // then
        assertThat(result).isSameAs(filter);
        verifyNoMoreInteractions(sqlFilterParser);
    }
}
