package dev.langchain4j.store.embedding.filter.parser.sql;

import dev.langchain4j.Experimental;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.FilterParser;
import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;
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
import net.sf.jsqlparser.statement.select.PlainSelect;

import java.net.URLEncoder;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR;

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
 * - YEAR/MONTH(CURDATE()): For example, {@code year = YEAR(CURDATE())} to get the current year. Provided {@link Clock} will be used to resolve {@code CURDATE()}.
 * - EXTRACT(YEAR/MONTH/WEEK/DAY/DOW/DOY/HOUR/MINUTE FROM CURRENT_DATE/CURRENT_TIME/CURRENT_TIMESTAMP): For example: {@code year = EXTRACT(YEAR FROM CURRENT_DATE)} to get the current year. Provided {@link Clock} will be used to resolve {@code CURRENT_DATE}.
 *
 * - Arithmetic: {@code +}, {@code -}, {@code *}, {@code /}. For example: {@code year = YEAR(CURDATE()) - 1} to get previous year.
 *
 * - Parentheses: {@code (name = 'Klaus' OR name = 'Francine') AND age = 18}. Expressions within parentheses are evaluated first.
 * </pre>
 * If you require additional operations,
 * please <a href="https://github.com/langchain4j/langchain4j/issues/new/choose">open an issue</a>.
 * <br>
 * <br>
 * Here are a few examples of how a {@code String} is parsed into a {@link Filter}:
 * <pre>
 * {@code name = 'Klaus'} -&gt; {@code key("name").eq("Klaus")}
 * {@code name = 'Klaus' AND age >= 18} -&gt; {@code key("name").eq("Klaus").and(key("age").gte(18))}
 * </pre>
 */
@Experimental
public class SqlFilterParser implements FilterParser {

    private final LocalDateTime localDateTime;

    /**
     * Creates an instance of {@code SqlFilterParser}.
     * <br>
     * By default, {@link Clock#systemDefaultZone()} will be used to get the current date and/or time when required.
     * For example, when parsing the SQL query
     * {@code SELECT * FROM fake_table WHERE year = EXTRACT(YEAR FROM CURRENT_DATE)},
     * the {@link Clock#systemDefaultZone()} will be used to resolve {@code CURRENT_DATE}.
     */
    public SqlFilterParser() {
        this(Clock.systemDefaultZone());
    }

    /**
     * Creates an instance of {@code SqlFilterParser}.
     *
     * @param clock A {@link Clock} to be used to get the current date and/or time when required.
     *              For example, when parsing the SQL query
     *              {@code SELECT * FROM fake_table WHERE year = EXTRACT(YEAR FROM CURRENT_DATE)},
     *              the provided {@link Clock} will be used to resolve {@code CURRENT_DATE}.
     */
    public SqlFilterParser(Clock clock) {
        this.localDateTime = LocalDateTime.now(ensureNotNull(clock, "clock"));
    }

    @Override
    public Filter parse(String sql) {

        if (!sql.toUpperCase().startsWith("SELECT")) {
            sql = "SELECT * FROM fake_table WHERE " + sql;
        }

        try {
            PlainSelect select = (PlainSelect) CCJSqlParserUtil.parse(sql);
            return mapParenthesis(select.getWhere());
        } catch (JSQLParserException e) {
            throw new RuntimeException(e);
        }
    }

    private Filter mapParenthesis(Expression expression) {
        if (expression instanceof BinaryExpression) {
            return mapBinaryExpression((BinaryExpression) expression);
        } else if (expression instanceof NotExpression) {
            return new Not(mapParenthesis(((NotExpression) expression).getExpression()));
        } else if (expression instanceof Parenthesis) {
            return mapParenthesis(((Parenthesis) expression).getExpression());
        } else if (expression instanceof InExpression) {
            return mapInExpression(((InExpression) expression));
        } else if (expression instanceof Between) {
            return mapBetween(((Between) expression));
        } else {
            throw illegalArgument("Unsupported expression: '%s'%s", expression, createGithubIssueLink(expression));
        }
    }

    private static String createGithubIssueLink(Expression unsupportedExpression) {
        try {
            return ". Please click the following link to open an issue on our GitHub: " +
                    "https://github.com/langchain4j/langchain4j/issues/new?labels=SqlFilterParser&title=SqlFilterParser:%20Support%20new%20expression%20type&body=" +
                    URLEncoder.encode(unsupportedExpression.toString(), "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private Filter mapBinaryExpression(BinaryExpression exp) {
        if (exp instanceof AndExpression) {
            return new And(mapParenthesis(exp.getLeftExpression()), mapParenthesis(exp.getRightExpression()));
        } else if (exp instanceof OrExpression) {
            return new Or(mapParenthesis(exp.getLeftExpression()), mapParenthesis(exp.getRightExpression()));
        } else if (exp instanceof EqualsTo) {
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
            throw illegalArgument("Unsupported expression: '%s'%s", exp, createGithubIssueLink(exp));
        }
    }

    private Filter mapInExpression(InExpression inExpression) {
        String key = ((Column) inExpression.getLeftExpression()).getColumnName();

        Collection<Object> comparisonValues = new ArrayList<>();
        inExpression.getRightExpression().accept(new ExpressionVisitorAdapter() {

            @Override
            public void visit(StringValue value) {
                comparisonValues.add(value.getValue());
            }

            @Override
            public void visit(LongValue value) {
                comparisonValues.add(value.getValue());
            }

            @Override
            public void visit(DoubleValue value) {
                comparisonValues.add(value.getValue());
            }
        });

        if (inExpression.isNot()) {
            return new IsNotIn(key, comparisonValues);
        } else {
            return new IsIn(key, comparisonValues);
        }
    }

    private Filter mapBetween(Between between) {
        String key = ((Column) between.getLeftExpression()).getColumnName();
        Comparable<?> from = getValue(between.getBetweenExpressionStart());
        Comparable<?> to = getValue(between.getBetweenExpressionEnd());
        return new IsGreaterThanOrEqualTo(key, from).and(new IsLessThanOrEqualTo(key, to));
    }

    private String getKey(BinaryExpression binaryExpression) {
        return ((Column) binaryExpression.getLeftExpression()).getColumnName();
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
        } else if (expression instanceof SignedExpression) {
            SignedExpression signedExpression = (SignedExpression) expression;
            if (signedExpression.getSign() == '-') {
                if (signedExpression.getExpression() instanceof LongValue) {
                    String stringValue = signedExpression.getExpression().toString();
                    return Long.parseLong("-" + stringValue);
                } else if (signedExpression.getExpression() instanceof DoubleValue) {
                    String stringValue = signedExpression.getExpression().toString();
                    return Double.parseDouble("-" + stringValue);
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
            }
            // TODO add other
        } else if (expression instanceof ExtractExpression) {
            ExtractExpression extractExpression = (ExtractExpression) expression;
            if (extractExpression.getExpression() instanceof TimeKeyExpression) {
                TimeKeyExpression timeKeyExpression = (TimeKeyExpression) extractExpression.getExpression();
                if (timeKeyExpression.getStringValue().equalsIgnoreCase("CURRENT_DATE")
                        || timeKeyExpression.getStringValue().equalsIgnoreCase("CURRENT_TIME")
                        || timeKeyExpression.getStringValue().equalsIgnoreCase("CURRENT_TIMESTAMP")) {
                    String field = extractExpression.getName().toUpperCase();
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
        } else if (expression instanceof Addition) {
            Comparable<?> left = getValue(((Addition) expression).getLeftExpression());
            Comparable<?> right = getValue(((Addition) expression).getRightExpression());
            if (left instanceof Long && right instanceof Long) {
                return (Long) left + (Long) right;
            } else if (left instanceof Double && right instanceof Double) {
                return (Double) left + (Double) right;
            }
        } else if (expression instanceof Subtraction) {
            Comparable<?> left = getValue(((Subtraction) expression).getLeftExpression());
            Comparable<?> right = getValue(((Subtraction) expression).getRightExpression());
            if (left instanceof Long && right instanceof Long) {
                return (Long) left - (Long) right;
            } else if (left instanceof Double && right instanceof Double) {
                return (Double) left - (Double) right;
            }
        } else if (expression instanceof Multiplication) {
            Comparable<?> left = getValue(((Multiplication) expression).getLeftExpression());
            Comparable<?> right = getValue(((Multiplication) expression).getRightExpression());
            if (left instanceof Long && right instanceof Long) {
                return (Long) left * (Long) right;
            } else if (left instanceof Double && right instanceof Double) {
                return (Double) left * (Double) right;
            }
        } else if (expression instanceof Division) {
            Comparable<?> left = getValue(((Division) expression).getLeftExpression());
            Comparable<?> right = getValue(((Division) expression).getRightExpression());
            if (left instanceof Long && right instanceof Long) {
                return (Long) left / (Long) right;
            } else if (left instanceof Double && right instanceof Double) {
                return (Double) left / (Double) right;
            }
        }

        throw illegalArgument("Unsupported expression: '%s'%s", expression, createGithubIssueLink(expression));
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
}
