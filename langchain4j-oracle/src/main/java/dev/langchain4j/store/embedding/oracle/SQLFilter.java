package dev.langchain4j.store.embedding.oracle;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotIn;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * <p>
 * A SQL expression which evaluates to a boolean result. This interface mirrors that of the {@link Filter} interface.
 * Where a <code>Filter</code> evaluates a boolean result in Java, a <code>SQLFilter</code> does so in SQL.
 * </p><p>
 * This interface is designed to interoperate with the JDBC API. The String returned by {@link #asSQLCondition()} can be
 * embedded within SQL that is passed to {@link Connection#prepareStatement(String)}. The String may include "?"
 * parameter markers. Parameter values are set when the <code>PreparedStatement</code> is passed to
 * {@link #setParameters(PreparedStatement, int)}.
 * </p>
 */
public interface SQLFilter {

    /**
     * <p>
     * Returns this filter as a SQL expression. The SQL expression returned by this method can appear within the WHERE
     * clause of a SELECT query. The expression may contain "?" characters that a {@link PreparedStatement} will
     * recognize as parameter markers. The values of any parameters are set when a <code>PreparedStatement</code> is
     * passed to the {@link #setParameters(PreparedStatement, int)} method of this filter.
     * </p><p>
     * An optional namespace parameter is applied to any identifiers which appear in the expression. This can be used
     * when the identifier must be prefixed with a table name. In the example below, sqlExpression has the value of
     * "example.x=?"
     * <pre>{@code
     * ResultSet example(Connection connection) {
     *   Filter filter = IsEqualTo("x", 9);
     *   SQLFilter sqlFilter = SQLIsEqualTo(filter);
     *
     *   // Prepare: "SELECT y FROM example WHERE x = ?"
     *   PreparedStatement preparedStatement = connection.prepareStatement(
     *     "SELECT y FROM example WHERE " + sqlFilter.getSQL();
     *
     *   // Set the parameter at index 1. (1 is the lowest index for JDBC)
     *   sqlFilter.set(preparedStatement, 1);
     *
     *   preparedStatement.closeOnCompletion();
     *   return preparedStatement.executeQuery();
     * }
     * }</pre>
     * </p>
     *
     * @param identifierOperator Namespace of any identifiers which appear in the filter. Not null.
     *
     * @return SQL filtering expression. Not null.
     */
    String asSQLCondition(UnaryOperator<String> identifierOperator);

    default String asSQLCondition() {
       return asSQLCondition(UnaryOperator.identity());
    }

    /**
     * Sets the value of any parameter markers in the SQL expression returned by {@link #asSQLCondition()}.
     *
     * @param preparedStatement Statement to set with a value. Not null.
     * @param parameterIndex Index to set with a value.
     *
     * @return The number of parameters which were set. May be 0.
     *
     * @throws SQLException If one is thrown from the PreparedStatement.
     */
    int setParameters(PreparedStatement preparedStatement, int parameterIndex) throws SQLException;

    /**
     * Returns a SQL filter that evaluates to the same result as a <code>Filter</code>.
     *
     *
     * @param filter Filter to map into a SQLFilter. Not null.
     *
     * @return The equivalent SQLFilter. Not null.
     *
     * @throws IllegalArgumentException If the Filter is not recognized, or null.
     */
    static SQLFilter from(Filter filter) {
        if (filter instanceof IsEqualTo) {
            return new SQLIsEqualTo((IsEqualTo) filter);
        }
        else if (filter instanceof IsNotEqualTo) {
            return new SQLIsNotEqualTo((IsNotEqualTo) filter);
        }
        else if (filter instanceof IsNotIn) {
            return new SQLIsNotIn((IsNotIn) filter);
        }
        else if (filter instanceof And) {
            return new SQLAnd((And)filter);
        }
        else if (filter instanceof Not) {
            return new SQLNot((Not)filter);
        }
        throw new IllegalArgumentException("Unrecognized filter: " + filter);
    }

    class SQLIsEqualTo implements SQLFilter {

        private final String key;
        private final Object comparisonValue;

        public SQLIsEqualTo(IsEqualTo isEqualTo) {
            this(isEqualTo.key(), isEqualTo.comparisonValue());
        }

        public SQLIsEqualTo(String key, Object comparisonValue) {
            this.key = key;
            this.comparisonValue = comparisonValue;
        }

        @Override
        public String asSQLCondition(UnaryOperator<String> identifierOperator) {
            // A conditional expression in SQL my result in TRUE, FALSE, or NULL. The NVL function maps NULL to FALSE.
            return "NVL(" + identifierOperator.apply(key) + " = ?, FALSE)";
        }

        @Override
        public int setParameters(PreparedStatement preparedStatement, int parameterIndex) throws SQLException {
            preparedStatement.setObject(parameterIndex, toJdbcObject(comparisonValue));
            return 1;
        }
    }

    class SQLAnd implements SQLFilter {

        private final SQLFilter left;

        private final SQLFilter right;

        public SQLAnd(And and) {
            this(from(and.left()), from(and.right()));
        }

        public SQLAnd(SQLFilter left, SQLFilter right) {
            this.left = left;
            this.right = right;
        }

        @Override
        public String asSQLCondition(UnaryOperator<String> identifierOperator) {
            return "(" + left.asSQLCondition(identifierOperator)
                    + " AND " + right.asSQLCondition(identifierOperator) + ")";
        }

        @Override
        public int setParameters(PreparedStatement preparedStatement, int parameterIndex) throws SQLException {
            int leftCount = left.setParameters(preparedStatement, parameterIndex);
            int rightCount = right.setParameters(preparedStatement, parameterIndex + leftCount);
            return leftCount + rightCount;
        }
    }

    class SQLNot implements SQLFilter {

        private final SQLFilter expression;

        public SQLNot(Not not) {
            this(from(not.expression()));
        }

        public SQLNot(SQLFilter expression) {
            this.expression = expression;
        }

        @Override
        public String asSQLCondition(UnaryOperator<String> identifierOperator) {
            return "NOT(" + expression.asSQLCondition(identifierOperator) + ")";
        }

        @Override
        public int setParameters(PreparedStatement preparedStatement, int parameterIndex) throws SQLException {
            return expression.setParameters(preparedStatement, parameterIndex);
        }
    }

    class SQLIsNotEqualTo implements SQLFilter {

        private final String key;

        private final Object comparisonValue;

        public SQLIsNotEqualTo(IsNotEqualTo isNotEqualTo) {
            this.key = isNotEqualTo.key();
            this.comparisonValue = isNotEqualTo.comparisonValue();
        }

        @Override
        public String asSQLCondition(UnaryOperator<String> identifierOperator) {
            // A SQL not-equals operator, "<>", will result in NULL if either the key or value are NULL. Instances
            // of IsNotEqual do not permit a null comparisonValue. So if the <> operator results in NULL, then the key
            // has a null value. A null value is not equal to a non-null value in Java, so the NVL function maps NULL to
            // TRUE.
            return "NVL(" + identifierOperator.apply(key) + " <> ?, TRUE)";
        }

        @Override
        public int setParameters(PreparedStatement preparedStatement, int parameterIndex) throws SQLException {
            preparedStatement.setObject(parameterIndex, toJdbcObject(comparisonValue));
            return 1;
        }
    }

    class SQLIsNotIn implements SQLFilter {

        private final String key;
        private final Collection<?> comparisonValues;

        public SQLIsNotIn(IsNotIn isNotIn) {
            this(isNotIn.key(), isNotIn.comparisonValues());
        }

        public SQLIsNotIn(String key, Collection<?> comparisonValues) {
            this.key = key;
            this.comparisonValues = comparisonValues;
        }

        @Override
        public String asSQLCondition(UnaryOperator<String> identifierOperator) {
            return "NVL(" + identifierOperator.apply(key) + " NOT IN ("
                    + Stream.generate(() -> "?")
                        .limit(comparisonValues.size())
                        .collect(Collectors.joining(", "))
                    + "), TRUE)";
        }

        @Override
        public int setParameters(PreparedStatement preparedStatement, int parameterIndex) throws SQLException {
            for (Object object : comparisonValues) {
                preparedStatement.setObject(parameterIndex++, toJdbcObject(object));
            }
            return comparisonValues.size();
        }
    }

    /**
     * Converts an object into one that can be passed to {@link PreparedStatement#setObject(int, Object)}. JDBC drivers
     * are only required to support object types listed in the JDBC Specification.
     *
     * @param object
     * @return
     */
    static Object toJdbcObject(Object object) {
        if (object instanceof UUID)
            return object.toString();

        return object;
    }
}
