package dev.langchain4j.store.embedding.oracle;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;

import java.sql.*;
import java.sql.SQLType;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A factory for {@link SQLFilter} implementations. The {@link #create(Filter, BiFunction)} creates a SQLFilter that
 * is equivalent to a given {@link Filter}.
 */
final class SQLFilters {

    /**
     * The SQL filter which does nothing. The SQLFilter interface is implemented to return an empty string from methods
     * that generate SQL expressions.
     */
    static final SQLFilter EMPTY = new SQLEmptyFilter();

    private SQLFilters() {}

    /**
     * Map of {@link Filter} classes to functions which construct the equivalent {@link SQLFilter}.
     */
    private static final Map<Class<? extends Filter>, FilterConstructor> CONSTRUCTORS;
    static {
        Map<Class<? extends Filter>, FilterConstructor> map = new HashMap<>();

        map.put(IsEqualTo.class, (filter, keyMapper) ->
            new SQLComparisonFilter((IsEqualTo) filter, keyMapper));

        map.put(IsNotEqualTo.class, (filter, keyMapper) ->
            new SQLComparisonFilter((IsNotEqualTo) filter, keyMapper));

        map.put(IsGreaterThan.class, (filter, keyMapper) ->
            new SQLComparisonFilter((IsGreaterThan) filter, keyMapper));

        map.put(IsGreaterThanOrEqualTo.class, (filter, keyMapper) ->
            new SQLComparisonFilter((IsGreaterThanOrEqualTo) filter, keyMapper));

        map.put(IsLessThan.class, (filter, keyMapper) ->
            new SQLComparisonFilter((IsLessThan) filter, keyMapper));

        map.put(IsLessThanOrEqualTo.class, (filter, keyMapper) ->
            new SQLComparisonFilter((IsLessThanOrEqualTo) filter, keyMapper));

        map.put(IsIn.class, (filter, keyMapper) ->
            new SQLInFilter((IsIn) filter, keyMapper));

        map.put(IsNotIn.class, (filter, keyMapper) ->
            new SQLInFilter((IsNotIn) filter, keyMapper));

        map.put(And.class, (filter, keyMapper) ->
            new SQLLogicalFilter((And) filter, keyMapper));

        map.put(Or.class, (filter, keyMapper) ->
            new SQLLogicalFilter((Or) filter, keyMapper));

        map.put(Not.class, (filter, keyMapper) ->
            new SQLNot((Not)filter, keyMapper));

        CONSTRUCTORS = Collections.unmodifiableMap(map);
    }


    /**
     * <p>
     * Returns a SQL filter that evaluates to the same result as a <code>Filter</code>.
     * </p><p>
     * A keyMapper function converts a {@link dev.langchain4j.data.document.Metadata} key into a SQL expression which
     * results in the value of that key, as a specific SQL data type. The SQL expression returned by a mapping function
     * might cast a column name to a specific type, as in "CAST(example AS VARCHAR)". The expression could also call the
     * JSON_VALUE function, as in "JSON_VALUE(metadata, '$.example' RETURNING NUMERIC)".
     * </p>
     *
     * @param filter Filter to replicate as a SQLFilter. May be null.
     *
     * @param keyMapper Function which maps {@link dev.langchain4j.data.document.Metadata} keys to a SQL expression.
     *                  This argument can not be null. The String and SQLType passed to this function will not be null.
     *                  This function must not return null.
     *
     * @return The equivalent SQLFilter, which may be {@link #EMPTY} if the input <code>Filter</code> is null.
     *
     * @throws IllegalArgumentException If the class of the Filter is not recognized.
     */
    static SQLFilter create(Filter filter, BiFunction<String, SQLType, String> keyMapper) {
        if (filter == null)
            return EMPTY;

        Class<? extends Filter> filterClass = filter.getClass();
        FilterConstructor constructor = CONSTRUCTORS.get(filterClass);

        if (constructor == null)
            throw new IllegalArgumentException("Unrecognized Filter class: " + filterClass);

        return constructor.construct(filter, keyMapper);
    }

    /**
     * A constructor of SQLFilter objects.
     */
    interface FilterConstructor {

        /**
         * Constructs a SQLFilter which performs the same filtering operation as a given {@link Filter}.
         *
         * @param filter Filter to replicate as a SQLFilter. Not null
         *
         * @param keyMapper Function which maps {@link dev.langchain4j.data.document.Metadata} keys to SQL identifiers.
         *                  The SQL identifier can be a simple column name, or it can be a more complex expression, such
         *                  as a call to the builtin "JSON_VALUE" function. This argument can not be null. The String
         *                  passed to this function will not be null. This function must not return a null result.
         */
        SQLFilter construct(Filter filter, BiFunction<String, SQLType, String> keyMapper);
    }

    /**
     * <p>
     * A SQL filter that compares a key to a value. Calls to {@link ##toSQL()} return a SQL
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

        /**
         * A SQL comparison condition with parameter markers and handling for a NULL/UNKNOWN result. Example:
         * <pre>
         * NVL(key > ?, FALSE)
         * </pre>
         */
        private final String sql;

        /**
         * The SQL data type that values are compared as. The comparison will operate on a key value and
         * {@link #comparisonValue} that are converted to this SQL type.
         */
        private final SQLType sqlType;

        /** The right side operand. This is set as the value for the "?" parameter marker */
        private final Object comparisonValue;

        SQLComparisonFilter(IsEqualTo isEqualTo, BiFunction<String, SQLType, String> keyMapper) {
            this(isEqualTo.key(), keyMapper, "=", isEqualTo.comparisonValue(), false);
        }

        SQLComparisonFilter(IsNotEqualTo isNotEqualTo, BiFunction<String, SQLType, String> keyMapper) {
            this(isNotEqualTo.key(), keyMapper, "<>", isNotEqualTo.comparisonValue(), true);
        }

        SQLComparisonFilter(IsGreaterThan isGreaterThan, BiFunction<String, SQLType, String> keyMapper) {
            this(isGreaterThan.key(), keyMapper, ">", isGreaterThan.comparisonValue(), false);
        }

        SQLComparisonFilter(IsGreaterThanOrEqualTo isGreaterThanOrEqualTo, BiFunction<String, SQLType, String> keyMapper) {
            this(isGreaterThanOrEqualTo.key(), keyMapper, ">=", isGreaterThanOrEqualTo.comparisonValue(), false);
        }

        SQLComparisonFilter(IsLessThan isLessThan, BiFunction<String, SQLType, String> keyMapper) {
            this(isLessThan.key(), keyMapper, "<", isLessThan.comparisonValue(), false);
        }

        SQLComparisonFilter(IsLessThanOrEqualTo isLessThanOrEqualTo, BiFunction<String, SQLType, String> keyMapper) {
            this(isLessThanOrEqualTo.key(), keyMapper, "<=", isLessThanOrEqualTo.comparisonValue(), false);
        }

        /**
         * <p>
         * Constructs a filter which applies a comparison operator to a key and value.
         * </p><p>
         * The behavior of comparison operators are defined here:
         * <a href="https://docs.oracle.com/en/database/oracle/oracle-database/23/sqlrf/Data-Type-Comparison-Rules.html">
         * here
         * </a>. This behavior is <em>mostly</em> consistent with that of the various {@link Filter} implementations,
         * but future work may be needed to address any inconsistencies.
         * </p><p>
         * An <code>isNullTrue</code> parameter should reflect what an equivalent {@link Filter} will do when an
         * instance of {@link Metadata} does not contain the key. This can be derived by looking for
         * {@code if (!metadata.containsKey(key))} in the various implementations of {@link Filter#test(Object)}. In
         * SQL terms: The filter uses "NVL" to handle a NULL/UNKNOWN result. The comparison value cannot be null, so a
         * NULL result means the metadata does not contain a key. Typically, comparing a null value to a non-null
         * should result in FALSE. There are is at least one exception to that, which is for a not-equals comparison:
         * A null value is not equal to a non-null value.
         * </p>
         *
         * @param key Identifies the key applied as the left side operand. Not null.
         *
         * @param keyMapper Function which maps {@link dev.langchain4j.data.document.Metadata} keys to SQL identifiers.
         *                  The SQL identifier can be a simple column name, or it can be a more complex expression, such
         *                  as a call to the builtin "JSON_VALUE" function. This argument can not be null. The String
         *                  passed to this function will not be null. This function must not return a null result.
         *
         * @param operator Operator between the left and right side operands. Not null.
         *
         * @param comparisonValue Value applied as the right side operand. Not null.
         *
         * @param isNullTrue Result of the filter when the metadata does not contain the key.
         */
        private <T> SQLComparisonFilter(
                String key, BiFunction<String, SQLType, String> keyMapper, String operator, T comparisonValue,
                boolean isNullTrue) {
            this.sqlType = toSQLType(comparisonValue);
            this.sql = "NVL(" + keyMapper.apply(key, sqlType) + " " + operator + " ?, " + isNullTrue + ")";
            this.comparisonValue = comparisonValue;
        }

        @Override
        public String toSQL() {
            return sql;
        }

        @Override
        public int setParameters(PreparedStatement preparedStatement, int parameterIndex) throws SQLException {
            preparedStatement.setObject(parameterIndex, toJdbcObject(comparisonValue), sqlType);
            return 1;
        }
    }

    /**
     * <p>
     * A SQL filter that combines the result of two other filters. Calls to {@link #toSQL()}
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

        /** The left side filter. This can be any SQL condition. */
        private final SQLFilter left;

        /** The right side filter. This can be any SQL condition. */
        private final SQLFilter right;

        /**
         * A SQL condition composed of a logical operator the {@link #left} and {@link #right} filters. See examples in
         * class-level JavaDoc. This SQL condition has no NVL for handling for a NULL/UNKNOWN result, because all
         * SQLFilter implementations are assumed to handle that: No SQLFilter implementation should yield a
         * NULL/UNKNOWN result.
         */
        private final String sql;

        SQLLogicalFilter(And and, BiFunction<String, SQLType, String> keyMapper) {
            this(and.left(), "AND", and.right(), keyMapper);
        }

        SQLLogicalFilter(Or or, BiFunction<String, SQLType, String> keyMapper) {
            this(or.left(), "OR", or.right(), keyMapper);
        }

        private SQLLogicalFilter(Filter left, String operator, Filter right, BiFunction<String, SQLType, String> keyMapper) {
            this(create(left, keyMapper), operator, create(right, keyMapper));
        }

        private SQLLogicalFilter(SQLFilter left, String operator, SQLFilter right) {
            this.left = left;
            this.right = right;
            this.sql = "(" + left.toSQL() + " " + operator + " " + right.toSQL() + ")";
        }

        @Override
        public String toSQL() {
            return sql;
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
     * A SQL filter that negates the result of another filter. Calls to {@link #toSQL()} return a
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
     */
    private static class SQLNot implements SQLFilter {

        /** Filter that is negated. This can be any SQL condition. */
        private final SQLFilter expression;

        /**
         * A SQL NOT condition with parameter markers. Example:
         * <pre>
         * NOT(key > ?)
         * </pre>
         * There is no NVL for handling for a NULL/UNKNOWN result, because all SQLFilter implementations are assumed to
         * handle that; No SQLFilter implementation should yield a NULL/UNKNOWN result.
         */
        private final String sql;

        SQLNot(Not not, BiFunction<String, SQLType, String> keyMapper) {
            this.expression = create(not.expression(), keyMapper);
            this.sql = "NOT(" + expression.toSQL() + ")";
        }

        @Override
        public String toSQL() {
            return sql;
        }

        @Override
        public int setParameters(PreparedStatement preparedStatement, int parameterIndex) throws SQLException {
            return expression.setParameters(preparedStatement, parameterIndex);
        }
    }

    /**
     * <p>
     * A SQL filter that checks if a key is in a set of values, or not. Calls to {@link #toSQL()}
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

        /**
         * A SQL IN condition with parameter markers and handling for a NULL/UNKNOWN result. Example:
         * <pre>
         * NVL(key NOT IN (?, ?, ?), TRUE)
         * </pre>
         */
        private final String sql;

        /**
         * The SQL data type that values are compared as. The IN or NOT clause will operate on a key value and
         * {@link #comparisonValues} that are converted to this SQL type.
         */
        private final SQLType sqlType;

        /** The values to search within. These are set as the values for "?" parameter markers */
        private final Collection<?> comparisonValues;

        SQLInFilter(IsIn isIn, BiFunction<String, SQLType, String> keyMapper) {
            this(isIn.key(), keyMapper, true, isIn.comparisonValues());
        }

        SQLInFilter(IsNotIn isNotIn, BiFunction<String, SQLType, String> keyMapper) {
            this(isNotIn.key(), keyMapper, false, isNotIn.comparisonValues());
        }

        /**
         * <p>
         * Constructs a filter which uses an "IN" or "NOT IN" condition.
         * </p><p>
         * The filter uses "NVL" to handle a NULL/UNKNOWN result. The comparison values cannot be null, so a NULL result
         * means the metadata does not contain a key. A null value is not in any set of values, so NVL is TRUE for a NOT
         * IN condition, and FALSE for an IN condition.
         * </p>
         *
         * @param key Identifies the key applied as the left side operand. Not null.
         *
         * @param keyMapper Function which maps {@link dev.langchain4j.data.document.Metadata} keys to SQL identifiers.
         *                  The SQL identifier can be a simple column name, or it can be a more complex expression, such
         *                  as a call to the builtin "JSON_VALUE" function. This argument can not be null. The String
         *                  passed to this function will not be null. This function must not return a null result.
         *
         * @param isIn <code>true</code> to construct an "IN" condition, or <code>false</code> to construct a "NOT IN"
         *             condition.
         *
         * @param comparisonValues Set of values to search within. Not null. Not empty.
         */
        private SQLInFilter(
                String key, BiFunction<String, SQLType, String> keyMapper, boolean isIn,
                Collection<?> comparisonValues) {
            this.sqlType = toSQLType(comparisonValues);
            this.sql = "NVL(" + keyMapper.apply(key, sqlType) + (isIn ? " IN " : " NOT IN ") + "("
                    + Stream.generate(() -> "?")
                        .limit(comparisonValues.size())
                        .collect(Collectors.joining(", "))
                    + "), "
                    + !isIn + ")"; // <-- 2nd argument to NVL
            this.comparisonValues = comparisonValues;
        }

        @Override
        public String toSQL() {
            return sql;
        }

        @Override
        public int setParameters(PreparedStatement preparedStatement, int parameterIndex) throws SQLException {
            for (Object object : comparisonValues) {
                preparedStatement.setObject(parameterIndex++, toJdbcObject(object), sqlType);
            }
            return comparisonValues.size();
        }
    }

    /**
     * <p>
     * A SQL filter which does nothing. An empty string is returned by all methods which generate SQL expressions.
     * </p><p>
     * This is a singleton. The {@link #EMPTY} constant should be the only instance of this class.
     * </p>
     */
    private static final class SQLEmptyFilter implements SQLFilter {

        private SQLEmptyFilter() {}

        @Override
        public String toSQL() {
            return "";
        }

        @Override
        public int setParameters(PreparedStatement preparedStatement, int parameterIndex) throws SQLException {
            return 0;
        }

        @Override
        public String asWhereClause() {
            return "";
        }
    }

    /**
     * Converts an object into one that can be passed to {@link PreparedStatement#setObject(int, Object, SQLType)}.
     * JDBC drivers are only required to support object types listed in the JDBC Specification, and this may not
     * include all object types supported by {@link Metadata}. Namely, {@link UUID}.
     *
     * @param object Object to convert. May be null.
     * @return The converted object, or the same object if no conversion is required. May be null.
     */
    static Object toJdbcObject(Object object) {
        if (object instanceof UUID)
            return object.toString();

        return object;
    }

    /**
     * <p>
     * Returns the SQL data type that a filter should use when binding a collection of objects with
     * {@link PreparedStatement#setObject(int, Object, SQLType)}, where all objects will be compared to a key value.
     * This method only checks for classes of objects which are supported by {@link Metadata}, as a {@link Filter} is
     * only supposed to operate on the values of a Metadata object.
     * </p><p>
     * This method will throw an IllegalArgumentException if the Collection contains objects of different classes, such
     * as {@code List.of("a", 0, 1.1d)}. It is not clear if users actually need that case to be supported or not. It
     * is possible to construct an IsIn or IsNotIn Filter with such a Collection, and the Filters do support it.
     * However, there are no tests which verify such a case, so it is thought that it might not be required. If this
     * case does need to be supported, consider decomposing the SQL IN or NOT IN expressions into multiple OR or AND
     * expressions, with each expression using a different SQL type.
     * </p>
     * @param objects Collection of objects that are converted into SQL data types. Not null. Not empty.
     * @return The SQL data type to convert objects into. Not null.
     */
    static SQLType toSQLType(Collection<?> objects) {
        Set<SQLType> jdbcTypes =
            objects.stream()
                    .map(SQLFilters::toSQLType)
                    .collect(Collectors.toSet());

        // Not supporting the case where a collection contains different classes of objects.
        if (jdbcTypes.size() != 1) {
            throw new IllegalArgumentException(
                    "Filters comparing a Collection of non-uniform object classes are not supported. The Collection " +
                            "contains objects of the following classes: " +
                    objects.stream()
                            .map(Object::getClass)
                            .map(Class::getSimpleName)
                            .collect(Collectors.joining(", ")));
        }

        return jdbcTypes.iterator().next();
    }

    /**
     * Returns the SQL data type that a filter should use when binding an object with
     * {@link PreparedStatement#setObject(int, Object, SQLType)}. This method only checks for classes of objects which
     * are supported by {@link Metadata}, as a {@link Filter} is only supposed to operate on the values of a Metadata
     * object.
     *
     * @param object Object that is converted into a SQL data type. Not null. Not empty.
     * @return The SQL data type to convert object into. Not null.
     */
    static SQLType toSQLType(Object object) {
        if (object instanceof Number) {
            if (object instanceof Float)
                return JDBCType.REAL; // REAL with default precision is a 32-bit floating point number
            else if (object instanceof Double)
                return JDBCType.FLOAT; // FLOAT with default precision is a 64-bit floating point number
            else if (object instanceof Integer)
                return JDBCType.INTEGER;
            else if (object instanceof Long)
                return JDBCType.NUMERIC; // NUMERIC is an integer with 38 decimal digits
            else
                throw new RuntimeException("Unsupported type: " + object.getClass());
        }
        else {
            // Compare null, String, UUID, and any other object that Metadata supports in the future as VARCHAR objects.
            // It is assumed that the getOsonFromMetadata object method in OracleEmbeddingStore will convert these
            // objects to String. A VARCHAR can store the information of a Java String without losing any information.
            return JDBCType.VARCHAR;
        }
    }
}
