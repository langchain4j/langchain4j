package dev.langchain4j.experimental.rag.content.retriever.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.model.chat.ChatModel;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.OngoingStubbing;

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

    @Test
    void execute_should_escape_values_with_comma_and_quotes() throws SQLException {
        Statement statement =
                mockStatementReturningSingleRow(List.of("name", "address"), "Alice \"AJ\" Jones", "1 Main St, Apt 2");

        String result = retriever.execute("SELECT name, address FROM customers", statement);

        assertThat(result).isEqualTo("name,address\n\"Alice \"\"AJ\"\" Jones\",\"1 Main St, Apt 2\"");
    }

    @Test
    void execute_should_quote_values_with_line_break() throws SQLException {
        Statement statement = mockStatementReturningSingleRow(List.of("name", "notes"), "Alice", "line1\nline2");

        String result = retriever.execute("SELECT name, notes FROM customers", statement);

        assertThat(result).isEqualTo("name,notes\nAlice,\"line1\nline2\"");
    }

    @Test
    void execute_should_quote_values_with_double_quotes_only() throws SQLException {
        Statement statement = mockStatementReturningSingleRow(List.of("name"), "5\" monitor");

        String result = retriever.execute("SELECT name FROM products", statement);

        assertThat(result).isEqualTo("name\n\"5\"\" monitor\"");
    }

    @Test
    void execute_should_not_quote_plain_values_and_should_handle_null() throws SQLException {
        Statement statement = mockStatementReturningSingleRow(List.of("id", "name"), 42, null);

        String result = retriever.execute("SELECT id, name FROM customers", statement);

        assertThat(result).isEqualTo("id,name\n42,");
    }

    @Test
    void execute_should_escape_column_names_with_comma_and_quotes() throws SQLException {
        Statement statement = mockStatementReturningSingleRow(
                List.of("concat(first_name, ' ', last_name)", "total \"count\""), "Alice Jones", 3);

        String result =
                retriever.execute("SELECT concat(first_name, ' ', last_name), count(*) FROM customers", statement);

        assertThat(result).isEqualTo("\"concat(first_name, ' ', last_name)\",\"total \"\"count\"\"\"\nAlice Jones,3");
    }

    @Test
    void execute_should_quote_values_with_carriage_return() throws SQLException {
        Statement statement = mockStatementReturningSingleRow(List.of("name", "notes"), "Alice", "line1\r\nline2");

        String result = retriever.execute("SELECT name, notes FROM customers", statement);

        assertThat(result).isEqualTo("name,notes\nAlice,\"line1\r\nline2\"");
    }

    @Test
    void execute_should_render_multiple_rows() throws SQLException {
        Statement statement = mockStatementReturningRows(
                List.of("id", "name"),
                List.of(Arrays.asList(1, "Alice"), Arrays.asList(2, "Bob, Jr."), Arrays.asList(3, null)));

        String result = retriever.execute("SELECT id, name FROM customers", statement);

        assertThat(result).isEqualTo("id,name\n1,Alice\n2,\"Bob, Jr.\"\n3,");
    }

    private static Statement mockStatementReturningSingleRow(List<String> columnNames, Object... rowValues)
            throws SQLException {
        return mockStatementReturningRows(columnNames, List.of(Arrays.asList(rowValues)));
    }

    private static Statement mockStatementReturningRows(List<String> columnNames, List<List<Object>> rows)
            throws SQLException {
        ResultSetMetaData metaData = mock(ResultSetMetaData.class);
        when(metaData.getColumnCount()).thenReturn(columnNames.size());
        for (int i = 1; i <= columnNames.size(); i++) {
            when(metaData.getColumnName(i)).thenReturn(columnNames.get(i - 1));
        }

        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getMetaData()).thenReturn(metaData);

        OngoingStubbing<Boolean> next = when(resultSet.next());
        for (int row = 0; row < rows.size(); row++) {
            next = next.thenReturn(true);
        }
        next.thenReturn(false);

        for (int i = 1; i <= columnNames.size(); i++) {
            OngoingStubbing<Object> value = when(resultSet.getObject(i));
            for (List<Object> row : rows) {
                value = value.thenReturn(row.get(i - 1));
            }
        }

        Statement statement = mock(Statement.class);
        when(statement.executeQuery(anyString())).thenReturn(resultSet);
        return statement;
    }
}
