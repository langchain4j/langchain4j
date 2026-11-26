package dev.langchain4j.store.embedding.filter.parser.sql;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import dev.langchain4j.Experimental;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.FilterParser;
import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;
import java.net.URLEncoder;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.expression.operators.arithmetic.Addition;
import net.sf.jsqlparser.expression.operators.arithmetic.Division;
import net.sf.jsqlparser.expression.operators.arithmetic.Multiplication;
import net.sf.jsqlparser.expression.operators.arithmetic.Subtraction;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.*;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import org.jspecify.annotations.Nullable;

/**
 * Parses an SQL "WHERE" clause into a {@link Filter} object using
 * <a href="https://github.com/JSQLParser/JSqlParser">JSqlParser</a>.
 * <br>
 * Can parse a complete SQL statement (e.g., {@code SELECT * FROM fake_table WHERE id = 7})
 * as well as just the contents of a "WHERE" clause (e.g., {@code id = 7}).
 * <br>
 * Currently, supports all SQL dialects supported by JSqlParser.
 * <br>
 * But we recommend using ANSI SQL or PostgreSQL as we cannot guarantee that this class will support all SQL dialects going forward.
 * <br>
 * <br>
 * Currently, the following operations are supported:
 * <pre>
 * - {@link IsEqualTo}: {@code name = 'Klaus'}
 * - {@link IsNotEqualTo}: {@code id != 7}
 * - {@link IsGreaterThan}: {@code age > 18}
 * - {@link IsGreaterThanOrEqualTo}: {@code age >= 18}
 * - {@link IsLessThan}: {@code age < 18}
 * - {@link IsLessThanOrEqualTo}: {@code age <= 18}
 * - {@link IsIn}: {@code name IN ('Klaus', 'Francine')}
 * - {@link IsNotIn}: {@code id NOT IN (1, 2, 3)}
 * - BETWEEN: {@code year BETWEEN 2000 AND 2020}: will be parsed into {@code key("year").gte(2000).and(key("year").lte(2020))}
 *
 * - {@link And}: {@code name = 'Klaus' AND age = 18}
 * - {@link Not}: {@code NOT(name = 'Klaus')} / {@code NOT name = 'Klaus'}
 * - {@link Or}: {@code name = 'Klaus' OR age = 18}
 *
 * - YEAR/MONTH/WEEKOFYEAR/DAYOFYEAR/DAYOFMONTH/DAYOFWEEK(CURDATE()): For example, {@code year = YEAR(CURDATE())} to get the current year. Provided {@link Clock} will be used to resolve {@code CURDATE()}.
 * - EXTRACT(YEAR/MONTH/WEEK/DAY/DOW/DOY/HOUR/MINUTE FROM CURRENT_DATE/CURRENT_TIME/CURRENT_TIMESTAMP): For example: {@code year = EXTRACT(YEAR FROM CURRENT_DATE)} to get the current year. Provided {@link Clock} will be used to resolve {@code CURRENT_DATE}.
 *
 * - Arithmetic: {@code +}, {@code -}, {@code *}, {@code /}. For example: {@code year = YEAR(CURDATE()) - 1} to get previous year.
 *
 * - Parentheses: {@code (name = 'Klaus' OR name = 'Francine') AND age = 18}. Expressions within parentheses are evaluated first.
 * </pre>
 * Values inside {@code IN}/{@code NOT IN} lists can be any of the expressions listed above,
 * for example {@code age IN (-1, 2 + 3)} or {@code year IN (YEAR(CURDATE()), YEAR(CURDATE()) - 1)}.
 * <br>
 * If you require additional operations,
 * please <a href="https://github.com/langchain4j/langchain4j/issues/new/choose">open an issue</a>.
 * <br>
 * <br>
 * Here are a few examples of how a {@code String} is parsed into a {@link Filter}:
 * <pre>
 * {@code name = 'Klaus'} -&gt; {@code key("name").eq("Klaus")}
 * {@code name = 'Klaus' AND age >= 18} -&gt; {@code key("name").eq("Klaus").and(key("age").gte(18))}
 * </pre>
 * <br>
 * {@code SqlFilterParser} has limits for very large or deeply nested filters:
 * by default, a filter with more than {@value #DEFAULT_MAX_CONDITIONS} conditions,
 * with parentheses nested more than {@value #DEFAULT_MAX_NESTING_DEPTH} levels deep,
 * or that takes longer than 5 seconds to parse, is rejected with an {@link IllegalArgumentException}.
 * Use {@link #builder()} to change these limits.
 * <br>
 * A filter that contains a {@code CASE} or {@code CAST} expression, a nested {@code SELECT} or an array
 * (for example, {@code ARRAY[1, 2]}) is rejected with an {@link IllegalArgumentException},
 * as these are not supported.
 */
@Experimental
public class SqlFilterParser implements FilterParser {

    private static final int DEFAULT_MAX_CONDITIONS = 100;
    private static final int DEFAULT_MAX_NESTING_DEPTH = 50;
    private static final Duration DEFAULT_PARSE_TIMEOUT = Duration.ofSeconds(5);

    private final LocalDateTime localDateTime;
    private final int maxConditions;
    private final int maxNestingDepth;
    private final long parseTimeoutMillis;

    /**
     * Creates an instance of {@code SqlFilterParser} with the default limits
     * (see {@link Builder#maxConditions(Integer)}, {@link Builder#maxNestingDepth(Integer)}
     * and {@link Builder#parseTimeout(Duration)}).
     * <br>
     * The current date and time are read from {@link Clock#systemDefaultZone()} once, when the parser is built.
     * They are used to resolve, for example, {@code CURRENT_DATE} in the SQL query
     * {@code SELECT * FROM fake_table WHERE year = EXTRACT(YEAR FROM CURRENT_DATE)}.
     * To resolve {@code CURRENT_DATE} to a later date, build a new parser.
     */
    public SqlFilterParser() {
        this(builder());
    }

    /**
     * Creates an instance of {@code SqlFilterParser} with the default limits
     * (see {@link Builder#maxConditions(Integer)}, {@link Builder#maxNestingDepth(Integer)}
     * and {@link Builder#parseTimeout(Duration)}).
     *
     * @param clock A {@link Clock} to read the current date and time from.
     *              The current date and time are read from this clock once, when the parser is built.
     *              They are used to resolve, for example, {@code CURRENT_DATE} in the SQL query
     *              {@code SELECT * FROM fake_table WHERE year = EXTRACT(YEAR FROM CURRENT_DATE)}.
     *              To resolve {@code CURRENT_DATE} to a later date, build a new parser.
     */
    public SqlFilterParser(Clock clock) {
        this(builder().clock(ensureNotNull(clock, "clock")));
    }

    /**
     * Creates a parser with the options of the given builder.
     * To create a parser, use {@link #builder()} and {@link Builder#build()}.
     * This constructor is for subclasses, which can use it to set the options of the parser, for example:
     * <pre>{@code
     * class MySqlFilterParser extends SqlFilterParser {
     *
     *     MySqlFilterParser() {
     *         super(SqlFilterParser.builder().maxConditions(5000));
     *     }
     * }
     * }</pre>
     *
     * @param builder the builder with the options of the parser
     * @throws IllegalArgumentException if the options are not valid, see {@link Builder#build()}
     * @since 1.25.0
     */
    protected SqlFilterParser(Builder builder) {
        this.localDateTime = LocalDateTime.now(getOrDefault(builder.clock, Clock::systemDefaultZone));
        this.maxConditions =
                ensureGreaterThanZero(getOrDefault(builder.maxConditions, DEFAULT_MAX_CONDITIONS), "maxConditions");
        this.maxNestingDepth = ensureGreaterThanZero(
                getOrDefault(builder.maxNestingDepth, DEFAULT_MAX_NESTING_DEPTH), "maxNestingDepth");
        Duration parseTimeout = getOrDefault(builder.parseTimeout, DEFAULT_PARSE_TIMEOUT);
        if (parseTimeout.isNegative() || parseTimeout.isZero()) {
            throw illegalArgument("parseTimeout must be greater than zero, but is: %s", parseTimeout);
        }
        this.parseTimeoutMillis = Math.max(1, MILLISECONDS.convert(parseTimeout));
    }

    /**
     * Creates a builder for {@code SqlFilterParser}, for example to change the limits for very large
     * or deeply nested filters:
     * <pre>{@code
     * SqlFilterParser parser = SqlFilterParser.builder()
     *         .maxConditions(5000)
     *         .maxNestingDepth(100)
     *         .build();
     * }</pre>
     *
     * @return a new builder
     * @since 1.25.0
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Parses an SQL "WHERE" clause (e.g., {@code id = 7}) or a complete SQL statement
     * (e.g., {@code SELECT * FROM fake_table WHERE id = 7}) into a {@link Filter}.
     *
     * @param sql the SQL to parse
     * @return the {@link Filter}
     * @throws IllegalArgumentException if the filter has more conditions than allowed
     *                                  (see {@link Builder#maxConditions(Integer)}),
     *                                  if its parentheses are nested deeper than allowed
     *                                  (see {@link Builder#maxNestingDepth(Integer)}),
     *                                  if parsing it takes longer than allowed
     *                                  (see {@link Builder#parseTimeout(Duration)}),
     *                                  if it is too complex to parse,
     *                                  or if it contains an expression that is not supported
     */
    @Override
    public Filter parse(String sql) {

        PreScan preScan = preScan(sql);
        if (preScan.nestingDepth() > maxNestingDepth) {
            throw illegalArgument(
                    "The SQL filter has parentheses nested %s levels deep, more than the maximum of %s. "
                            + "Increase it with SqlFilterParser.builder().maxNestingDepth(...)",
                    preScan.nestingDepth(), maxNestingDepth);
        }
        if (preScan.unsupportedConstruct() != null) {
            throw illegalArgument(
                    "The SQL filter contains %s, which is not supported", preScan.unsupportedConstruct());
        }

        if (!isKeywordAt(sql, 0, "SELECT")) {
            sql = "SELECT * FROM fake_table WHERE " + sql;
        }

        try {
            Statement statement = CCJSqlParserUtil.parse(
                    sql, parser -> parser.withTimeOut(parseTimeoutMillis).withAllowComplexParsing(false));
            return mapWhereClause(((PlainSelect) statement).getWhere());
        } catch (JSQLParserException e) {
            throw parsingFailed(e);
        } catch (StackOverflowError e) {
            throw tooComplex(e);
        }
    }

    private RuntimeException parsingFailed(JSQLParserException e) {
        if (hasCause(e, StackOverflowError.class)) {
            return tooComplex(e);
        } else if (hasCause(e, TimeoutException.class)) {
            return illegalArgument(
                    "Parsing the SQL filter took longer than the maximum of %s ms. "
                            + "Increase it with SqlFilterParser.builder().parseTimeout(...)",
                    parseTimeoutMillis);
        } else if (hasCause(e, InterruptedException.class)) {
            Thread.currentThread().interrupt();
        }
        return new RuntimeException(e);
    }

    private static IllegalArgumentException tooComplex(Throwable cause) {
        return new IllegalArgumentException("The SQL filter is too complex to parse", cause);
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> causeType) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable cause = throwable; cause != null && visited.add(cause); cause = cause.getCause()) {
            if (causeType.isInstance(cause)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The result of {@link #preScan(String)}.
     *
     * @param nestingDepth         the maximum depth of nested parentheses
     * @param unsupportedConstruct the first construct that is not supported, or {@code null} if none was found
     */
    record PreScan(int nestingDepth, @Nullable String unsupportedConstruct) {}

    /**
     * Reads the SQL once, in linear time and without parsing it, to find the maximum depth of nested parentheses
     * and the first construct that cannot be turned into a {@link Filter} and can make parsing slow:
     * a {@code CASE} expression (found by its {@code WHEN}, as {@code case} can also be a column name),
     * a {@code CAST(...)} expression, a {@code SELECT} other than the one at the start, or an array
     * ({@code [} that does not follow a column name, for example {@code ARRAY[1, 2]}, or that is nested
     * inside another {@code [}). A single subscript of a column, for example {@code a[1]}, is not rejected.
     * <br>
     * String literals, quoted identifiers and comments are skipped the same way JSqlParser skips them,
     * and comments count as whitespace around keywords.
     * The end of Oracle {@code q'(...)'} and {@code $$...$$} quoted text cannot be found without parsing,
     * so from the first {@code q'} or {@code $$} that starts a token on, nothing is skipped any more:
     * every opening parenthesis counts, closing ones are ignored, and constructs are looked for everywhere,
     * also inside string literals and comments. The depth can then be higher than the real one, but never lower,
     * and a construct is never missed.
     */
    static PreScan preScan(String sql) {
        int lastBlockCommentEnd = sql.lastIndexOf("*/");
        String unsupportedConstruct = null;
        boolean afterQuotedTextWithUnknownEnd = false;
        int depth = 0;
        int maxDepth = 0;
        int bracketDepth = 0;
        char previousCharacter = ' ';
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            boolean skipping = !afterQuotedTextWithUnknownEnd;
            if (c == '(') {
                maxDepth = Math.max(maxDepth, ++depth);
            } else if (c == ')') {
                if (skipping) {
                    depth = Math.max(0, depth - 1);
                }
            } else if (skipping && (c == '\'' || c == '"' || c == '`')) {
                i = indexOfClosingQuote(sql, i);
            } else if (skipping && sql.startsWith("\\'", i)) {
                i++;
            } else if (skipping && isCommentAt(sql, i, lastBlockCommentEnd)) {
                i = indexOfCommentEnd(sql, i) - 1;
                continue;
            } else if (skipping && isQuotedTextWithUnknownEndAt(sql, i)) {
                afterQuotedTextWithUnknownEnd = true;
                i += c == '$' ? 1 : 2; // the rest of $$, or of q' and the delimiter that follows it
            } else if (c == '[') {
                if (unsupportedConstruct == null && (bracketDepth > 0 || !isColumnSubscript(sql, i))) {
                    unsupportedConstruct = "an array";
                }
                bracketDepth++;
            } else if (c == ']') {
                bracketDepth = Math.max(0, bracketDepth - 1);
            } else if (unsupportedConstruct == null) {
                unsupportedConstruct = unsupportedKeywordAt(sql, i, previousCharacter, lastBlockCommentEnd);
            }
            if (!Character.isWhitespace(c)) {
                previousCharacter = c;
            }
        }
        return new PreScan(maxDepth, unsupportedConstruct);
    }

    private static boolean isQuotedTextWithUnknownEndAt(String sql, int index) {
        boolean startsToken = index == 0 || !isIdentifierPart(sql.charAt(index - 1));
        return startsToken && (sql.startsWith("$$", index) || sql.startsWith("q'", index) || sql.startsWith("Q'", index));
    }

    private static @Nullable String unsupportedKeywordAt(
            String sql, int index, char previousCharacter, int lastBlockCommentEnd) {
        if (isKeywordAt(sql, index, "WHEN")) {
            return "a CASE expression";
        } else if (isKeywordAt(sql, index, "CAST")
                && nextCharacterOutsideComments(sql, index + "CAST".length(), lastBlockCommentEnd) == '(') {
            return "a CAST expression";
        } else if (index > 0 && isKeywordAt(sql, index, "SELECT") && previousCharacter != '.') {
            return "a nested SELECT";
        }
        return null;
    }

    private static boolean isCommentAt(String sql, int index, int lastBlockCommentEnd) {
        return sql.startsWith("--", index)
                || sql.startsWith("//", index)
                || (sql.startsWith("/*", index) && lastBlockCommentEnd >= index + 2);
    }

    /**
     * Returns the index of the first character after the comment that starts at the given index.
     */
    private static int indexOfCommentEnd(String sql, int commentStartIndex) {
        if (sql.startsWith("/*", commentStartIndex)) {
            return sql.indexOf("*/", commentStartIndex + 2) + 2;
        }
        return indexOfLineEnd(sql, commentStartIndex);
    }

    private static char nextCharacterOutsideComments(String sql, int index, int lastBlockCommentEnd) {
        int i = index;
        while (i < sql.length()) {
            if (Character.isWhitespace(sql.charAt(i))) {
                i++;
            } else if (isCommentAt(sql, i, lastBlockCommentEnd)) {
                i = indexOfCommentEnd(sql, i);
            } else {
                return sql.charAt(i);
            }
        }
        return ' ';
    }

    private static boolean isColumnSubscript(String sql, int bracketIndex) {
        int end = bracketIndex - 1;
        while (end >= 0 && Character.isWhitespace(sql.charAt(end))) {
            end--;
        }
        if (end < 0) {
            return false;
        }
        char c = sql.charAt(end);
        if (c == '"' || c == '`') {
            return true;
        }
        int start = end;
        while (start >= 0 && isIdentifierPart(sql.charAt(start))) {
            start--;
        }
        return start < end && !isKeywordAt(sql, start + 1, "ARRAY");
    }

    private static boolean isKeywordAt(String sql, int index, String keyword) {
        int end = index + keyword.length();
        return (index == 0 || !isIdentifierPart(sql.charAt(index - 1)))
                && sql.regionMatches(true, index, keyword, 0, keyword.length())
                && (end == sql.length() || !isIdentifierPart(sql.charAt(end)));
    }

    private static boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '#' || c == '@';
    }

    private static int indexOfClosingQuote(String sql, int openingQuoteIndex) {
        char quote = sql.charAt(openingQuoteIndex);
        for (int i = openingQuoteIndex + 1; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (quote == '\'' && sql.startsWith("\\'", i)) {
                return i + 1;
            } else if (c == quote) {
                boolean escapedQuote = quote != '`' && i + 1 < sql.length() && sql.charAt(i + 1) == quote;
                if (!escapedQuote) {
                    return i;
                }
                i++;
            }
        }
        return sql.length();
    }

    private static int indexOfLineEnd(String sql, int commentStartIndex) {
        for (int i = commentStartIndex; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '\n' || c == '\r') {
                return i;
            }
        }
        return sql.length();
    }

    private Filter mapWhereClause(@Nullable Expression whereClause) {
        List<Expression> expressions = inPostOrder(whereClause);

        long conditions =
                expressions.stream().filter(SqlFilterParser::isCondition).count();
        if (conditions > maxConditions) {
            throw illegalArgument(
                    "The SQL filter has %s conditions, more than the maximum of %s. "
                            + "Increase it with SqlFilterParser.builder().maxConditions(...)",
                    conditions, maxConditions);
        }

        Deque<Filter> mapped = new ArrayDeque<>();
        for (Expression expression : expressions) {
            if (expression instanceof AndExpression) {
                Filter right = mapped.pop();
                mapped.push(new And(mapped.pop(), right));
            } else if (expression instanceof OrExpression) {
                Filter right = mapped.pop();
                mapped.push(new Or(mapped.pop(), right));
            } else if (expression instanceof NotExpression) {
                mapped.push(new Not(mapped.pop()));
            } else if (isCondition(expression)) {
                mapped.push(mapCondition(expression));
            }
        }
        return mapped.pop();
    }

    private static boolean isCondition(@Nullable Expression expression) {
        return !(expression instanceof AndExpression
                || expression instanceof OrExpression
                || expression instanceof NotExpression
                || expression instanceof Parenthesis);
    }

    private static List<Expression> inPostOrder(@Nullable Expression whereClause) {
        List<Expression> reversedPostOrder = new ArrayList<>();
        List<Expression> toVisit = new ArrayList<>();
        toVisit.add(whereClause);
        while (!toVisit.isEmpty()) {
            Expression expression = toVisit.remove(toVisit.size() - 1);
            reversedPostOrder.add(expression);
            if (expression instanceof AndExpression || expression instanceof OrExpression) {
                BinaryExpression binaryExpression = (BinaryExpression) expression;
                toVisit.add(binaryExpression.getLeftExpression());
                toVisit.add(binaryExpression.getRightExpression());
            } else if (expression instanceof NotExpression notExpression) {
                toVisit.add(notExpression.getExpression());
            } else if (expression instanceof Parenthesis parenthesis) {
                toVisit.add(parenthesis.getExpression());
            }
        }
        Collections.reverse(reversedPostOrder);
        return reversedPostOrder;
    }

    private Filter mapCondition(@Nullable Expression expression) {
        if (expression instanceof BinaryExpression) {
            return mapBinaryExpression((BinaryExpression) expression);
        } else if (expression instanceof InExpression) {
            return mapInExpression(((InExpression) expression));
        } else if (expression instanceof Between) {
            return mapBetween(((Between) expression));
        } else {
            throw unsupportedExpression(expression);
        }
    }

    private static IllegalArgumentException unsupportedExpression(@Nullable Expression expression) {
        String type = expression == null ? "null" : expression.getClass().getSimpleName();
        return illegalArgument(
                "Unsupported expression of type %s. Please click the following link to open an issue on our GitHub: "
                        + "https://github.com/langchain4j/langchain4j/issues/new?labels=SqlFilterParser"
                        + "&title=SqlFilterParser:%%20Support%%20new%%20expression%%20type&body=%s",
                type, URLEncoder.encode(type, UTF_8));
    }

    private Filter mapBinaryExpression(BinaryExpression exp) {
        if (exp instanceof EqualsTo) {
            return new IsEqualTo(getKey(exp), getValue(exp));
        } else if (exp instanceof NotEqualsTo) {
            return new IsNotEqualTo(getKey(exp), getValue(exp));
        } else if (exp instanceof net.sf.jsqlparser.expression.operators.relational.GreaterThan) {
            return new IsGreaterThan(getKey(exp), getValue(exp));
        } else if (exp instanceof net.sf.jsqlparser.expression.operators.relational.GreaterThanEquals) {
            return new IsGreaterThanOrEqualTo(getKey(exp), getValue(exp));
        } else if (exp instanceof MinorThan) {
            return new IsLessThan(getKey(exp), getValue(exp));
        } else if (exp instanceof MinorThanEquals) {
            return new IsLessThanOrEqualTo(getKey(exp), getValue(exp));
        } else {
            throw unsupportedExpression(exp);
        }
    }

    private Filter mapInExpression(InExpression inExpression) {
        String key = getKey(inExpression.getLeftExpression());

        Expression rightExpression = inExpression.getRightExpression();
        if (!(rightExpression instanceof ExpressionList<?> expressionList)) {
            throw unsupportedExpression(rightExpression);
        }

        Collection<Object> comparisonValues = new ArrayList<>();
        for (Expression expression : expressionList) {
            comparisonValues.add(getValue(expression));
        }

        if (inExpression.isNot()) {
            return new IsNotIn(key, comparisonValues);
        } else {
            return new IsIn(key, comparisonValues);
        }
    }

    private Filter mapBetween(Between between) {
        String key = getKey(between.getLeftExpression());
        Comparable<?> from = getValue(between.getBetweenExpressionStart());
        Comparable<?> to = getValue(between.getBetweenExpressionEnd());
        Filter range = new IsGreaterThanOrEqualTo(key, from).and(new IsLessThanOrEqualTo(key, to));
        return between.isNot() ? new Not(range) : range;
    }

    private String getKey(BinaryExpression binaryExpression) {
        return getKey(binaryExpression.getLeftExpression());
    }

    private String getKey(Expression expression) {
        if (expression instanceof Column column) {
            return column.getColumnName();
        } else if (expression instanceof Function function) {
            String name = function.getName();
            if (name.equalsIgnoreCase("YEAR")
                    || name.equalsIgnoreCase("MONTH")
                    || name.equalsIgnoreCase("WEEKOFYEAR")
                    || name.equalsIgnoreCase("DAYOFYEAR")
                    || name.equalsIgnoreCase("DAYOFMONTH")
                    || name.equalsIgnoreCase("DAYOFWEEK")) {
                ExpressionList<?> parameters = function.getParameters();
                if (parameters != null && parameters.size() == 1) {
                    Expression parameter = parameters.get(0);
                    if (parameter instanceof Column parameterColumn) {
                        return parameterColumn.getColumnName();
                    }
                }
            }
        }
        throw unsupportedExpression(expression);
    }

    private Comparable<?> getValue(BinaryExpression binaryExpression) {
        return getValue(binaryExpression.getRightExpression());
    }

    private Comparable<?> getValue(Expression expression) {
        if (expression instanceof StringValue) {
            return ((StringValue) expression).getValue();
        } else if (expression instanceof LongValue) {
            return ((LongValue) expression).getValue();
        } else if (expression instanceof DoubleValue) {
            return ((DoubleValue) expression).getValue();
        } else if (expression instanceof SignedExpression signedExpression) {
            char sign = signedExpression.getSign();
            Expression innerExpression = signedExpression.getExpression();
            if (sign == '-' || sign == '+') {
                if (innerExpression instanceof LongValue) {
                    return Long.parseLong(sign + innerExpression.toString());
                } else if (innerExpression instanceof DoubleValue) {
                    return Double.parseDouble(sign + innerExpression.toString());
                }
            }
        } else if (expression instanceof Function) {
            Function function = (Function) expression;
            if (function.getName().equalsIgnoreCase("YEAR")) {
                ExpressionList<?> parameters = function.getParameters();
                if (parameters.size() == 1 && parameters.get(0) instanceof Function) {
                    Function function2 = (Function) parameters.get(0);
                    if (function2.getName().equalsIgnoreCase("CURDATE")) {
                        return currentYear();
                    }
                }
            } else if (function.getName().equalsIgnoreCase("MONTH")) {
                ExpressionList<?> parameters = function.getParameters();
                if (parameters.size() == 1 && parameters.get(0) instanceof Function) {
                    Function function2 = (Function) parameters.get(0);
                    if (function2.getName().equalsIgnoreCase("CURDATE")) {
                        return currentMonth();
                    }
                }
            } else if (function.getName().equalsIgnoreCase("WEEKOFYEAR")) {
                ExpressionList<?> parameters = function.getParameters();
                if (parameters.size() == 1 && parameters.get(0) instanceof Function function2) {
                    if (function2.getName().equalsIgnoreCase("CURDATE")) {
                        return currentWeekOfYear();
                    }
                }
            } else if (function.getName().equalsIgnoreCase("DAYOFYEAR")) {
                ExpressionList<?> parameters = function.getParameters();
                if (parameters.size() == 1 && parameters.get(0) instanceof Function) {
                    Function function2 = (Function) parameters.get(0);
                    if (function2.getName().equalsIgnoreCase("CURDATE")) {
                        return currentDayOfYear();
                    }
                }
            } else if (function.getName().equalsIgnoreCase("DAYOFMONTH")) {
                ExpressionList<?> parameters = function.getParameters();
                if (parameters.size() == 1 && parameters.get(0) instanceof Function function2) {
                    if (function2.getName().equalsIgnoreCase("CURDATE")) {
                        return currentDayOfMonth();
                    }
                }
            } else if (function.getName().equalsIgnoreCase("DAYOFWEEK")) {
                ExpressionList<?> parameters = function.getParameters();
                if (parameters.size() == 1 && parameters.get(0) instanceof Function function2) {
                    if (function2.getName().equalsIgnoreCase("CURDATE")) {
                        return currentDayOfWeek();
                    }
                }
            }
        } else if (expression instanceof ExtractExpression) {
            ExtractExpression extractExpression = (ExtractExpression) expression;
            if (extractExpression.getExpression() instanceof TimeKeyExpression) {
                TimeKeyExpression timeKeyExpression = (TimeKeyExpression) extractExpression.getExpression();
                if (timeKeyExpression.getStringValue().equalsIgnoreCase("CURRENT_DATE")
                        || timeKeyExpression.getStringValue().equalsIgnoreCase("CURRENT_TIME")
                        || timeKeyExpression.getStringValue().equalsIgnoreCase("CURRENT_TIMESTAMP")) {
                    String field = extractExpression.getName().toUpperCase(Locale.ROOT);
                    switch (field) {
                        case "YEAR":
                            return currentYear();
                        case "MONTH":
                            return currentMonth();
                        case "WEEK":
                            return currentWeekOfYear();
                        case "DAY":
                            return currentDayOfMonth();
                        case "DOW":
                            return currentDayOfWeek();
                        case "DOY":
                            return currentDayOfYear();
                        case "HOUR":
                            return currentHour();
                        case "MINUTE":
                            return currentMinute();
                        // TODO add other
                    }
                } else {
                    // TODO parse timestamp?
                }
            }
        } else if (isArithmetic(expression)) {
            return calculate((BinaryExpression) expression);
        }

        throw unsupportedExpression(expression);
    }

    private static boolean isArithmetic(Expression expression) {
        return expression instanceof Addition
                || expression instanceof Subtraction
                || expression instanceof Multiplication
                || expression instanceof Division;
    }

    private Comparable<?> calculate(BinaryExpression arithmeticExpression) {
        Deque<BinaryExpression> operations = new ArrayDeque<>();
        Expression leftmostOperand = arithmeticExpression;
        while (isArithmetic(leftmostOperand)) {
            BinaryExpression operation = (BinaryExpression) leftmostOperand;
            operations.push(operation);
            leftmostOperand = operation.getLeftExpression();
        }

        Comparable<?> result = getValue(leftmostOperand);
        while (!operations.isEmpty()) {
            BinaryExpression operation = operations.pop();
            result = calculate(operation, result, getValue(operation.getRightExpression()));
        }
        return result;
    }

    private static Comparable<?> calculate(BinaryExpression operation, Comparable<?> left, Comparable<?> right) {
        if (left instanceof Long leftLong && right instanceof Long rightLong) {
            if (operation instanceof Addition) {
                return leftLong + rightLong;
            } else if (operation instanceof Subtraction) {
                return leftLong - rightLong;
            } else if (operation instanceof Multiplication) {
                return leftLong * rightLong;
            } else if (operation instanceof Division) {
                return leftLong / rightLong;
            }
        } else if (left instanceof Double leftDouble && right instanceof Double rightDouble) {
            if (operation instanceof Addition) {
                return leftDouble + rightDouble;
            } else if (operation instanceof Subtraction) {
                return leftDouble - rightDouble;
            } else if (operation instanceof Multiplication) {
                return leftDouble * rightDouble;
            } else if (operation instanceof Division) {
                return leftDouble / rightDouble;
            }
        }
        throw unsupportedExpression(operation);
    }

    private long currentYear() {
        return localDateTime.getYear();
    }

    private long currentMonth() {
        return localDateTime.getMonthValue();
    }

    private long currentWeekOfYear() {
        return localDateTime.get(WEEK_OF_WEEK_BASED_YEAR);
    }

    private long currentDayOfMonth() {
        return localDateTime.getDayOfMonth();
    }

    private long currentDayOfWeek() {
        return localDateTime.getDayOfWeek().getValue();
    }

    private long currentDayOfYear() {
        return localDateTime.getDayOfYear();
    }

    private long currentHour() {
        return localDateTime.getHour();
    }

    private long currentMinute() {
        return localDateTime.getMinute();
    }

    // TODO FallbackStrategy? FAIL/IGNORE_UNSUPPORTED?

    /**
     * A builder for {@link SqlFilterParser}.
     *
     * @since 1.25.0
     */
    public static class Builder {

        private @Nullable Clock clock;
        private @Nullable Integer maxConditions;
        private @Nullable Integer maxNestingDepth;
        private @Nullable Duration parseTimeout;

        Builder() {}

        /**
         * Sets the {@link Clock} to read the current date and time from.
         * They are used to resolve, for example, {@code CURRENT_DATE} in
         * {@code year = EXTRACT(YEAR FROM CURRENT_DATE)}.
         * The current date and time are read from this clock once, when the parser is built;
         * to resolve {@code CURRENT_DATE} to a later date, build a new parser.
         * <p>
         * Default value is {@link Clock#systemDefaultZone()}.
         *
         * @param clock the clock; {@code null} means the default
         * @return this builder
         * @since 1.25.0
         */
        public Builder clock(@Nullable Clock clock) {
            this.clock = clock;
            return this;
        }

        /**
         * Sets the maximum number of conditions in a filter.
         * Each comparison (for example {@code age >= 18}), {@code IN} and {@code BETWEEN} counts as one condition,
         * also an {@code IN} with many values. {@code AND}, {@code OR}, {@code NOT} and parentheses do not count.
         * For example, {@code name = 'Klaus' AND (age >= 18 OR city IN ('Berlin', 'Munich'))} has 3 conditions.
         * A filter with more conditions is rejected with an {@link IllegalArgumentException}.
         * <p>
         * Must be at least 1. Set it to {@link Integer#MAX_VALUE} to effectively remove the limit,
         * but keep in mind that a {@link Filter} with very many conditions can be too deep for some
         * embedding stores to evaluate, and that parsing thousands of conditions can take longer than the default
         * {@link #parseTimeout(Duration)} on a slow or busy machine.
         * <p>
         * Default value is {@value SqlFilterParser#DEFAULT_MAX_CONDITIONS}.
         *
         * @param maxConditions the maximum number of conditions; {@code null} means the default
         * @return this builder
         * @since 1.25.0
         */
        public Builder maxConditions(@Nullable Integer maxConditions) {
            this.maxConditions = maxConditions;
            return this;
        }

        /**
         * Sets the maximum depth of nested parentheses in a filter.
         * All parentheses count, including those of function calls and {@code IN} lists,
         * except the ones inside string literals, quoted identifiers and comments.
         * For example, {@code (name = 'Klaus' OR (age >= 18 AND city IN ('Berlin', 'Munich')))} has a depth of 3.
         * A filter nested deeper is rejected with an {@link IllegalArgumentException} before it is parsed.
         * <p>
         * Must be at least 1. Set it to {@link Integer#MAX_VALUE} to effectively remove the limit,
         * but keep in mind that parsing deeply nested parentheses can take very long
         * (see {@link #parseTimeout(Duration)}).
         * <p>
         * Default value is {@value SqlFilterParser#DEFAULT_MAX_NESTING_DEPTH}.
         *
         * @param maxNestingDepth the maximum depth of nested parentheses; {@code null} means the default
         * @return this builder
         * @since 1.25.0
         */
        public Builder maxNestingDepth(@Nullable Integer maxNestingDepth) {
            this.maxNestingDepth = maxNestingDepth;
            return this;
        }

        /**
         * Sets the maximum time to parse the SQL of a filter.
         * If parsing takes longer, it is stopped and the filter is rejected with an {@link IllegalArgumentException}.
         * <p>
         * This is a backstop: most filters are parsed within a few milliseconds,
         * filters nested too deep are rejected before they are parsed (see {@link #maxNestingDepth(Integer)}),
         * and so are some constructs that are slow to parse and not supported
         * ({@code CASE} and {@code CAST} expressions, nested {@code SELECT}s and arrays).
         * The timeout bounds the time spent on any other SQL that is slow to parse.
         * <p>
         * Very large filters, for example with thousands of conditions or {@code IN} values,
         * can take several seconds to parse on a slow or busy machine.
         * If you use such filters, increase the timeout as well.
         * <p>
         * Must be greater than zero. Default value is 5 seconds.
         *
         * @param parseTimeout the maximum time to parse a filter; {@code null} means the default
         * @return this builder
         * @since 1.25.0
         */
        public Builder parseTimeout(@Nullable Duration parseTimeout) {
            this.parseTimeout = parseTimeout;
            return this;
        }

        /**
         * Builds the {@link SqlFilterParser}.
         *
         * @return the parser
         * @throws IllegalArgumentException if {@code maxConditions} or {@code maxNestingDepth} is less than 1,
         *                                  or if {@code parseTimeout} is not greater than zero
         * @since 1.25.0
         */
        public SqlFilterParser build() {
            return new SqlFilterParser(this);
        }
    }
}
