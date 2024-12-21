package dev.langchain4j.store.embedding.oracle;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;
import oracle.jdbc.OracleType;

import java.io.StringReader;
import java.sql.PreparedStatement;
import java.sql.SQLException;
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

    /**
     * The maximum length of a VARCHAR in Oracle Database. This assumes the worst case scenario where the database's
     * initialization parameter, "MAX_STRING_SIZE", is not set to "EXTENDED".
     */
    private static final int MAX_VARCHAR_LENGTH = 4000;

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
            SQLInFilter.create((IsIn) filter, keyMapper));

        map.put(IsNotIn.class, (filter, keyMapper) ->
            SQLInFilter.create((IsNotIn) filter, keyMapper));

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
     *                  This argument can not be null. The String and OracleType passed to this function will not be null.
     *                  This function must not return null.
     *
     * @return The equivalent SQLFilter, which may be {@link #EMPTY} if the input <code>Filter</code> is null.
     *
     * @throws IllegalArgumentException If the class of the Filter is not recognized.
     */
    static SQLFilter create(Filter filter, BiFunction<String, OracleType, String> keyMapper) {
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
        SQLFilter construct(Filter filter, BiFunction<String, OracleType, String> keyMapper);
    }

    /**
     * <p>
     * A SQL filter that compares a key to a value. Calls to {@link #toSQL()} return a SQL
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
        private final OracleType sqlType;

        /** The right side operand. This is set as the value for the "?" parameter marker */
        private final Object comparisonValue;

        SQLComparisonFilter(IsEqualTo isEqualTo, BiFunction<String, OracleType, String> keyMapper) {
            this(isEqualTo.key(), keyMapper, "=", isEqualTo.comparisonValue(), false);
        }

        SQLComparisonFilter(IsNotEqualTo isNotEqualTo, BiFunction<String, OracleType, String> keyMapper) {
            this(isNotEqualTo.key(), keyMapper, "<>", isNotEqualTo.comparisonValue(), true);
        }

        SQLComparisonFilter(IsGreaterThan isGreaterThan, BiFunction<String, OracleType, String> keyMapper) {
            this(isGreaterThan.key(), keyMapper, ">", isGreaterThan.comparisonValue(), false);
        }

        SQLComparisonFilter(IsGreaterThanOrEqualTo isGreaterThanOrEqualTo, BiFunction<String, OracleType, String> keyMapper) {
            this(isGreaterThanOrEqualTo.key(), keyMapper, ">=", isGreaterThanOrEqualTo.comparisonValue(), false);
        }

        SQLComparisonFilter(IsLessThan isLessThan, BiFunction<String, OracleType, String> keyMapper) {
            this(isLessThan.key(), keyMapper, "<", isLessThan.comparisonValue(), false);
        }

        SQLComparisonFilter(IsLessThanOrEqualTo isLessThanOrEqualTo, BiFunction<String, OracleType, String> keyMapper) {
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
                String key, BiFunction<String, OracleType, String> keyMapper, String operator, T comparisonValue,
                boolean isNullTrue) {

            this.sqlType = toOracleType(comparisonValue);

            if (sqlType == OracleType.CLOB) {
                // DBMS_LOB.COMPARE must be used for a comparison of CLOB values. Comparison operators like "=" or "<"
                // cannot have a CLOB operand. The COMPARE function is similar to String.compareTo(String), returning
                // -1, 0, or 1 for less-than, equal-to, and greater-than, respectively.
                sql = "NVL(" +
                        "DBMS_LOB.COMPARE(" + keyMapper.apply(key, sqlType) + ", ?) " + operator + " 0, "
                        + isNullTrue + ")";
            }
            else {
                sql = "NVL(" + keyMapper.apply(key, sqlType) + " " + operator + " ?, " + isNullTrue + ")";
            }

            this.comparisonValue = comparisonValue;
        }

        @Override
        public String toSQL() {
            return sql;
        }

        @Override
        public int setParameters(PreparedStatement preparedStatement, int parameterIndex) throws SQLException {
            setObject(preparedStatement, parameterIndex, comparisonValue, sqlType);
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

        SQLLogicalFilter(And and, BiFunction<String, OracleType, String> keyMapper) {
            this(and.left(), "AND", and.right(), keyMapper);
        }

        SQLLogicalFilter(Or or, BiFunction<String, OracleType, String> keyMapper) {
            this(or.left(), "OR", or.right(), keyMapper);
        }

        private SQLLogicalFilter(Filter left, String operator, Filter right, BiFunction<String, OracleType, String> keyMapper) {
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

        SQLNot(Not not, BiFunction<String, OracleType, String> keyMapper) {
            this(create(not.expression(), keyMapper));
        }

        SQLNot(SQLFilter expression) {
            this.expression = expression;
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
        private final OracleType sqlType;

        /** The values to search within. These are set as the values for "?" parameter markers */
        private final Collection<?> comparisonValues;

        static SQLFilter create(IsIn isIn, BiFunction<String, OracleType, String> keyMapper) {
            return create(isIn.key(), keyMapper, true, isIn.comparisonValues());
        }

        static SQLFilter create(IsNotIn isNotIn, BiFunction<String, OracleType, String> keyMapper) {
            return create(isNotIn.key(), keyMapper, false, isNotIn.comparisonValues());
        }

        static SQLFilter create(
                String key, BiFunction<String, OracleType, String> keyMapper, boolean isIn,
                Collection<?> comparisonValues) {

            Set<OracleType> sqlTypes =
                    comparisonValues.stream()
                            .map(SQLFilters::toOracleType)
                            .collect(Collectors.toSet());
            Iterator<OracleType> sqlTypeIterator = sqlTypes.iterator();
            OracleType sqlType = sqlTypes.iterator().next();

            // IN and NOT IN conditions can operate on multiple data types, but the keyMapper needs to return the key as
            // a single data type. The IN and NOT IN conditions cannot operate on a CLOB, even if that's the only type.
            if (!sqlTypeIterator.hasNext() && sqlType != OracleType.CLOB) {
                return new SQLInFilter(key, keyMapper, isIn, comparisonValues, sqlType);
            }

            // Replicate IN and NOT IN conditions as a sequence of OR conditions: "key = value0 OR key = value1 OR ..."
            SQLFilter orFilter =
                comparisonValues.stream()
                        .<SQLFilter>map(object -> new SQLComparisonFilter(key, keyMapper, "=", object, false))
                        .reduce((left, right) -> new SQLLogicalFilter(left, "OR", right))
                        .orElse(EMPTY);

            return isIn ? orFilter : new SQLNot(orFilter);
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
                String key, BiFunction<String, OracleType, String> keyMapper, boolean isIn,
                Collection<?> comparisonValues, OracleType sqlType) {
            this.sqlType = sqlType;
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
                setObject(preparedStatement, parameterIndex++, object, sqlType);
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
     * Sets a Java object as a parameter value of a PreparedStatement.
     *
     * @param preparedStatement PreparedStatement to set with a parameter. Not null.
     * @param parameterIndex Parameter index to set. The first index is 1.
     * @param object Object to set. Not null.
     * @param sqlType SQL data type that the object is converted to. Not null.
     *
     * @throws SQLException If a JDBC API call results in an error, perhaps because the statement or connection is
     * closed, or because a Java-to-SQL conversion is not supported.
     */
    private static void setObject(
            PreparedStatement preparedStatement, int parameterIndex, Object object, OracleType sqlType)
            throws SQLException {

        Object jdbcObject = toJdbcObject(object);

        if (jdbcObject instanceof String && sqlType == OracleType.CLOB) {
            String string = (String)jdbcObject;
            int length = string.length();

            // Convert the String into a VARCHAR if the length is small enough. Oracle Database supports an implicit
            // conversion of VARCHAR to CLOB, so this should be safe. A conversion to VARCHAR will avoid the additional
            // database calls that create a CLOB and write data to it (these calls are part of Oracle JDBC's internal
            // implementation of setCharacterStream).
            if (length < MAX_VARCHAR_LENGTH) {
                preparedStatement.setString(parameterIndex, (String) jdbcObject);
            }
            else {
                // Oracle JDBC converts a Reader into CLOB if setCharacterStream is called without a length argument.
                preparedStatement.setCharacterStream(parameterIndex, new StringReader(string));
            }
        }
        else {
            preparedStatement.setObject(parameterIndex, jdbcObject, sqlType);
        }
    }

    /**
     * <p>
     * Converts an object into one that can be passed to
     * {@link PreparedStatement#setObject(int, Object, java.sql.SQLType)}. JDBC drivers are only required to support
     * object types listed in the JDBC Specification, and this may not include all object types supported by
     * {@link Metadata}. Namely, {@link UUID}.
     * </p>
     * @param object Object to convert. May be null.
     * @return The converted object, or the same object if no conversion is required. May be null.
     */
    private static Object toJdbcObject(Object object) {
        if (object instanceof UUID) {
            return object.toString();
        }
        else {
            return object;
        }
    }

    /**
     * <p></p>
     * Returns the SQL data type that a filter should use when binding an object with
     * {@link PreparedStatement#setObject(int, Object, java.sql.SQLType)}. This method only checks for classes of
     * objects which are supported by {@link Metadata}, as a {@link Filter} is only supposed to operate on the values of
     * a Metadata object.
     * </p><p>
     * This method MUST return a SQL data type that allows for a lossless conversion of the Java object by Oracle JDBC.
     * For instance, this method should NOT return BINARY_FLOAT for a Double object, as the conversion would lose
     * precision of the decimal digits. Likewise, this method should NOT return VARCHAR for a String object, as a
     * String's length can exceed the maximum length of a VARCHAR.
     * </p>
     *
     * @param object Object that is converted into a SQL data type. Not null. Not empty.
     * @return The SQL data type to convert object into. Not null.
     */
    static OracleType toOracleType(Object object) {
        if (object instanceof Number) {
            if (object instanceof Float) {
                return OracleType.BINARY_FLOAT;
            }
            else if (object instanceof Double) {
                return OracleType.BINARY_DOUBLE;
            }
            else if (object instanceof Integer || object instanceof Long) {
                // NUMBER is an integer with up to 38 decimal digits. It can represent any value of an Integer or Long.
                return OracleType.NUMBER;
            }
            else {
                // May need to add more branches above, if Metadata supports new object classes.
                throw new IllegalArgumentException("Unexpected object class: " + object.getClass());
            }
        }
        else if (object instanceof String) {
            // This String will be compared to another character value, and the length of that other character value is
            // not known. It cannot be assumed that the other character value's length is small enough to be a VARCHAR.
            // For this reason, the two character values should be compared as CLOBs.
            return OracleType.CLOB;

        }
        else {
            // Compare null, UUID, and any other object that Metadata supports in the future as VARCHAR objects.
            // It is assumed that the getOsonFromMetadata object method in OracleEmbeddingStore will convert these
            // objects to String. If the String length is 4k or less, then a VARCHAR can store the information of
            // without losing any information. Otherwise, the object should be converted into a CLOB.
            return OracleType.VARCHAR2;
        }
    }

}
