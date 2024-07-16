package dev.langchain4j.store.embedding.oracle;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A collection of classes which implement the {@link SQLFilter} interface.
 */
final class SQLFilters {

    private SQLFilters() {}

    /**
     * Map of {@link Filter} classes to functions which create the equivalent {@link SQLFilter}.
     */
    static final Map<Class<? extends Filter>, Function<? super Filter, ? extends SQLFilter>> FILTER_MAP;
    static {
        Map<Class<? extends Filter>, Function<? super Filter, ? extends SQLFilter>> map = new HashMap<>();

        map.put(IsEqualTo.class, filter -> new SQLComparisonFilter((IsEqualTo) filter));
        map.put(IsNotEqualTo.class, filter -> new SQLComparisonFilter((IsNotEqualTo) filter));
        map.put(IsGreaterThan.class, filter -> new SQLComparisonFilter((IsGreaterThan) filter));
        map.put(IsGreaterThanOrEqualTo.class, filter -> new SQLComparisonFilter((IsGreaterThanOrEqualTo) filter));
        map.put(IsLessThan.class, filter -> new SQLComparisonFilter((IsLessThan) filter));
        map.put(IsLessThanOrEqualTo.class, filter -> new SQLComparisonFilter((IsLessThanOrEqualTo) filter));

        map.put(IsIn.class, filter -> new SQLInFilter((IsIn) filter));
        map.put(IsNotIn.class, filter -> new SQLInFilter((IsNotIn) filter));

        map.put(And.class, filter -> new SQLLogicalFilter((And) filter));
        map.put(Or.class, filter -> new SQLLogicalFilter((Or) filter));
        map.put(Not.class, filter -> new SQLNot((Not)filter));

        FILTER_MAP = Collections.unmodifiableMap(map);
    }

    /**
     * <p>
     * A SQL filter that compares a key to a value. Calls to {@link ##asSQLExpression(UnaryOperator)} return a SQL
     * expression of the following form:
     * </p><pre>
     * {key} {operator} ?
     * </pre><p>
     * Examples:
     * </p><ul><li>
     * name = ?
     * </li><li>
     * age >= ?
     * </li></ul>
     */
    private static class SQLComparisonFilter implements SQLFilter {

        /** The operator between the left and right side operands. This can be "=", or "<", or ">=", etc */
        private final String operator;

        /** The left side operand. This is an identifier in the database, such as a column name, or a JSON field. */
        private final String key;

        /** The right side operand. This is set as the value for the "?" parameter marker */
        private final Object comparisonValue;

        /** The result when the operation yields NULL (or UNKNOWN) */
        private final boolean isNullTrue;

        SQLComparisonFilter(IsEqualTo isEqualTo) {
            this(isEqualTo.key(), "=", isEqualTo.comparisonValue(), false);
        }

        SQLComparisonFilter(IsNotEqualTo isNotEqualTo) {
            this(isNotEqualTo.key(), "<>", isNotEqualTo.comparisonValue(), true);
        }

        SQLComparisonFilter(IsGreaterThan isGreaterThan) {
            this(isGreaterThan.key(), ">", isGreaterThan.comparisonValue(), false);
        }

        SQLComparisonFilter(IsGreaterThanOrEqualTo isGreaterThanOrEqualTo) {
            this(isGreaterThanOrEqualTo.key(), ">=", isGreaterThanOrEqualTo.comparisonValue(), false);
        }

        SQLComparisonFilter(IsLessThan isLessThan) {
            this(isLessThan.key(), "<", isLessThan.comparisonValue(), false);
        }

        SQLComparisonFilter(IsLessThanOrEqualTo isLessThanOrEqualTo) {
            this(isLessThanOrEqualTo.key(), "<=", isLessThanOrEqualTo.comparisonValue(), false);
        }

        /**
         * <p>
         * Constructs a filter which that a key and comparison value to a SQL operator.
         * </p><p>
         * The behavior of comparison operators are defined here:
         * <a href="https://docs.oracle.com/en/database/oracle/oracle-database/23/sqlrf/Data-Type-Comparison-Rules.html">
         * here
         * </a>. This behavior is <em>mostly</em> consistent with that of the various {@link Filter} implementations.
         * Future work may be needed to address any inconsistencies.
         * </p><p>
         * An <code>isNullTrue</code> parameter should reflect what an equivalent {@link Filter} will do when an
         * instance of {@link Metadata} does not contain the key. This can be derived by looking for
         * {@code if (!metadata.containsKey(key))} in the various implementations of {@link Filter#test(Object)}.
         * </p>
         * @param key Identifier applied on the left side of the operand. Not null.
         * @param operator Operator between the left and right side operands. Not null.
         * @param comparisonValue Value applied on the right side of the operand. Not null.
         * @param isNullTrue Result when the metadata does not contain the key.
         */
        private <T> SQLComparisonFilter(String key, String operator, T comparisonValue, boolean isNullTrue) {
            this.key = key;
            this.comparisonValue = comparisonValue;
            this.operator = operator;
            this.isNullTrue = isNullTrue;
        }

        @Override
        public String asSQLExpression(UnaryOperator<String> idMapper) {
            // In cases where the operator results in NULL, the NVL function maps NULL to the nullResult.
            return "NVL(" + idMapper.apply(key) + " " + operator + " ?, " + isNullTrue + ")";
        }

        @Override
        public int setParameters(PreparedStatement preparedStatement, int parameterIndex) throws SQLException {
            preparedStatement.setObject(parameterIndex, toJdbcObject(comparisonValue));
            return 1;
        }
    }

    /**
     * <p>
     * A SQL filter that combines the result of two other filters. Calls to {@link #asSQLExpression(UnaryOperator)}
     * return a SQL expression of the following form:
     * </p><pre>
     * ({left} {AND|OR} {right})
     * </pre><p>
     * Examples:
     * </p><ul><li>
     * (name = ? OR age >= ?)
     * </li><li>
     * (name IN (?, ?, ?) OR age > ?)
     * </li></ul>
     */
    private static class SQLLogicalFilter implements SQLFilter {

        /** Operator between the left and right filters. This can be "AND" or "OR" */
        private final String operator;

        /** The left side filter. This can be any SQL condition. */
        private final SQLFilter left;

        /** The right side filter. This can be any SQL condition. */
        private final SQLFilter right;

        SQLLogicalFilter(And and) {
            this(and.left(), "AND", and.right());
        }

        SQLLogicalFilter(Or or) {
            this(or.left(), "OR", or.right());
        }

        private SQLLogicalFilter(Filter left, String operator, Filter right) {
            this(SQLFilter.fromFilter(left), operator, SQLFilter.fromFilter(right));
        }

        private SQLLogicalFilter(SQLFilter left, String operator, SQLFilter right) {
            this.operator = operator;
            this.left = left;
            this.right = right;
        }

        @Override
        public String asSQLExpression(UnaryOperator<String> idMapper) {
            return "(" + left.asSQLExpression(idMapper)
                    + " " + operator + " "
                    + right.asSQLExpression(idMapper) + ")";
        }

        @Override
        public int setParameters(PreparedStatement preparedStatement, int parameterIndex) throws SQLException {
            int leftCount = left.setParameters(preparedStatement, parameterIndex);
            int rightCount = right.setParameters(preparedStatement, parameterIndex + leftCount);
            return leftCount + rightCount;
        }
    }

    /**
     * <p>
     * A SQL filter that negates the result of another filter. Calls to {@link #asSQLExpression(UnaryOperator)} return a
     * SQL expression of the following form:
     * </p><pre>
     * NOT({filter})
     * </pre><p>
     * Examples:
     * <ul><li>
     * NOT(name = ?)
     * </li><li>
     * NOT(age > ? AND age < ?)
     * </li></ul>
     * </p>
     *
     */
    private static class SQLNot implements SQLFilter {

        /** Filter that is negated. This can be any SQL condition. */
        private final SQLFilter expression;

        SQLNot(Not not) {
            this.expression = SQLFilter.fromFilter(not.expression());
        }

        @Override
        public String asSQLExpression(UnaryOperator<String> idMapper) {
            return "NOT(" + expression.asSQLExpression(idMapper) + ")";
        }

        @Override
        public int setParameters(PreparedStatement preparedStatement, int parameterIndex) throws SQLException {
            return expression.setParameters(preparedStatement, parameterIndex);
        }
    }

    /**
     * <p>
     * A SQL filter that checks if a key is in a set of values, or not. Calls to {@link #asSQLExpression(UnaryOperator)}
     * return a SQL expression of the following form:
     * </p><pre>
     * {key} [NOT ]{IN}(?[,?]*)
     * </pre><p>
     * Examples:
     * <ul><li>
     * name IN (?, ?, ?)
     * </li><li>
     * age NOT IN (?, ?)
     * </li>
     * </ul>
     */
    private static class SQLInFilter implements SQLFilter {

        /** <code>true</code> if this is an "IN" filter, or false if it is a "NOT IN" filter */
        private final boolean isIn;

        /** The key to search for. This is an identifier in the database, such as a column name, or a JSON field. */
        private final String key;

        /** The values to search within. These are set as the values for "?" parameter markers */
        private final Collection<?> comparisonValues;

        SQLInFilter(IsIn isIn) {
            this(isIn.key(), true, isIn.comparisonValues());
        }

        SQLInFilter(IsNotIn isNotIn) {
            this(isNotIn.key(), false, isNotIn.comparisonValues());
        }

        private SQLInFilter(String key, boolean isIn, Collection<?> comparisonValues) {
            this.isIn = isIn;
            this.key = key;
            this.comparisonValues = comparisonValues;
        }

        @Override
        public String asSQLExpression(UnaryOperator<String> idMapper) {
            // Example: "NVL(id IN (?,?,?), FALSE)"
            return "NVL(" + idMapper.apply(key)
                    + (isIn ? " IN " : " NOT IN ") + "("
                    + Stream.generate(() -> "?")
                        .limit(comparisonValues.size())
                        .collect(Collectors.joining(", "))
                    + "), " + !isIn + ")";
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
     * are only required to support object types listed in the JDBC Specification, and this may not include all object
     * types supported by {@link Metadata}. Namely, {@link UUID}.
     *
     * @param object Object to convert. May be null.
     * @return The converted object, or the same object if no conversion is required. May be null.
     */
    static Object toJdbcObject(Object object) {
        if (object instanceof UUID)
            return object.toString();

        return object;
    }
}
