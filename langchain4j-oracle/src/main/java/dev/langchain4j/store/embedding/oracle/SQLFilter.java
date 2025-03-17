package dev.langchain4j.store.embedding.oracle;

import dev.langchain4j.store.embedding.filter.Filter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.function.UnaryOperator;

/**
 * <p>
 * A SQL expression which evaluates to a boolean result. This interface is used generate SQL expressions that perform
 * the operation of a {@link Filter} interface. This allows filtering operations to occur in the database, rather than
 * locally via {@link Filter#test(Object)}.
 * </p><p>
 * This interface is designed to interoperate with the JDBC API. The String returned by {@link #toSQL()} can be
 * embedded within SQL that is passed to {@link Connection#prepareStatement(String)}. The String may include "?"
 * parameter markers. Parameter values are set when the <code>PreparedStatement</code> is passed to
 * {@link #setParameters(PreparedStatement, int)}. The use of parameter markers can offer a performance benefit when the
 * same SQL is repeatedly executed, only with different parameter values. A JDBC driver may cache the statement for
 * reuse, and the database may avoid parsing of the same SQL statement.
 * </p><p>
 * Instances of SQLFilter can be created with {@link SQLFilters#create(Filter, UnaryOperator)}. The create method
 * accepts a key mapping function which translates a {@link dev.langchain4j.data.document.Metadata} key into a SQL
 * expression which identifies the key in the database. This translation could be as simple as appending a table name
 * to a column name. It can also be a more complex translation, such as using the builtin JSON_VALUE function to extract
 * the key from a JSON column.
 * </p><p>
 * The code example below illustrates the concepts described by the paragraphs above:
 * <pre>{@code
 * ResultSet example(Connection connection) {
 *   Filter filter = IsEqualTo("x", 9);
 *   SQLFilter sqlFilter = SQLFilters.create(filter, key -> "example." + key);
 *
 *   // Prepare: "SELECT y FROM example WHERE example.x = ?"
 *   PreparedStatement preparedStatement = connection.prepareStatement(
 *     "SELECT y FROM example WHERE " + sqlFilter.toSQL();
 *
 *   // Set the parameter at index 1. (1 is the lowest index for JDBC)
 *   sqlFilter.set(preparedStatement, 1);
 *
 *   // Don't forget to close the statement :)
 *   preparedStatement.closeOnCompletion();
 *
 *   return preparedStatement.executeQuery();
 * }
 * }</pre>
 */
interface SQLFilter {

    /**
     * <p>
     * Returns this filter as a SQL conditional expression. The expression returned by this method can appear within the
     * WHERE clause of a SELECT query. The expression may contain "?" characters that a {@link PreparedStatement} will
     * recognize as parameter markers. The values of any parameters are set when a <code>PreparedStatement</code> is
     * passed to the {@link #setParameters(PreparedStatement, int)} method of this filter.
     * </p><p>
     * This method returns an empty string if called on the {@link SQLFilters#EMPTY} instance.
     * </p>
     *
     * @return SQL expression which evaluates to the result of this filter. Not null.
     */
    String toSQL();

    /**
     * Returns this SQL filter as a WHERE clause, or returns an empty string if this is the {@link SQLFilters#EMPTY}
     * filter.
     *
     * @return SQL expression that evaluates to the result of this filter. Not null.
     */
    default String asWhereClause() {
        return " WHERE " + toSQL();
    }

    /**
     * Sets the value of any parameter markers in the SQL expression returned by {@link #toSQL()}.
     *
     * @param preparedStatement Statement to set with a value. Not null.
     *
     * @param parameterIndex Index to set with a value. For JDBC, the first index is 1.
     *
     * @return The number of parameters that were set. May be 0.
     *
     * @throws SQLException If one is thrown from the PreparedStatement.
     */
    int setParameters(PreparedStatement preparedStatement, int parameterIndex) throws SQLException;

}
