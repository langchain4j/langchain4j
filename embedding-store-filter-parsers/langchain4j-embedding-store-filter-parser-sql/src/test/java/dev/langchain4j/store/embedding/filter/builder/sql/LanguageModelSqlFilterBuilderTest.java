package dev.langchain4j.store.embedding.filter.builder.sql;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.filter.parser.sql.SqlFilterParser;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.mockito.Mockito.*;

class LanguageModelSqlFilterBuilderTest {

    TableDefinition tableDefinition = TableDefinition.builder()
            .name("table")
            .addColumn("column", "type")
            .build();

    SqlFilterParser sqlFilterParser = spy(new SqlFilterParser());

    @ParameterizedTest
    @ValueSource(strings = {
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
    @ValueSource(strings = {
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
}
