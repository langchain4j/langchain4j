package dev.langchain4j.store.embedding.filter.parser.sql;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.FilterParser;
import dev.langchain4j.store.embedding.filter.comparison.GreaterThan;
import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.*;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.PlainSelect;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Parses an SQL "WHERE" clause into a {@link Filter} object using
 * <a href="https://github.com/JSQLParser/JSqlParser">JSqlParser</a>.
 * Currently, supports all SQL dialects supported by JSqlParser, but we recommend using ANSI SQL as we cannot guarantee
 * that this class will support all SQL dialects going forward.
 * <br>
 * Currently, the following operations are supported:
 * <pre>
 * - {@link Equal}: {@code name = 'Klaus'}, {@code age = 18}
 * - {@link NotEqual}: {@code name != 'Klaus'}, {@code name != 18}
 * - {@link GreaterThan}: {@code age > 18}
 * - {@link GreaterThanOrEqual}: {@code age >= 18}
 * - {@link LessThan}: {@code age < 18}
 * - {@link LessThanOrEqual}: {@code age <= 18}
 * - {@link In}: {@code name IN ('Klaus', 'Francine')}, {@code age IN (18, 19)}
 * - {@link NotIn}: {@code id NOT IN ('1', '2', '3')}, {@code id NOT IN (1, 2, 3)}
 *
 * - {@link And}: {@code name = 'Klaus' AND age = 18}
 * - {@link Not}: {@code NOT(name = 'Klaus')}, {@code NOT name = 'Klaus'}
 * - {@link Or}: {@code name = 'Klaus' OR age = 18}
 *
 * - Parentheses can be used: {@code (name = 'Klaus' OR name = 'Francine') AND age = 18}
 * </pre>
 * If you require additional operations,
 * please <a href="https://github.com/langchain4j/langchain4j/issues/new/choose">open an issue</a>.
 * <br>
 * Here are a few examples of how a {@code String} is parsed into a {@link Filter}:
 * <pre>
 * {@code name = 'Klaus'} -&gt; {@code key("name").eq("Klaus")}
 * {@code name = 'Klaus' AND age >= 18} -&gt; {@code key("name").eq("Klaus").and(key("age").gte(18))}
 * </pre>
 */
public class SqlFilterParser implements FilterParser {

    /**
     * Parses an SQL "WHERE" clause into a {@link Filter} object.
     * Can parse a complete SQL statement (e.g., {@code SELECT * FROM fake_table WHERE id = 7})
     * as well as just the contents of a "WHERE" clause (e.g., {@code id = 7}).
     *
     * @param sql SQL "WHERE" clause
     * @return A {@link Filter} object
     */
    @Override
    public Filter parse(String sql) {

        if (!sql.toUpperCase().startsWith("SELECT")) {
            sql = "SELECT * FROM fake_table WHERE " + sql;
        }

        try {
            PlainSelect select = (PlainSelect) CCJSqlParserUtil.parse(sql);
            return map(select.getWhere());
        } catch (JSQLParserException e) {
            throw new RuntimeException(e);
        }
    }

    private Filter map(Expression expression) {
        if (expression instanceof BinaryExpression) {
            return map((BinaryExpression) expression);
        } else if (expression instanceof NotExpression) {
            return new Not(map(((NotExpression) expression).getExpression()));
        } else if (expression instanceof Parenthesis) {
            return map(((Parenthesis) expression).getExpression());
        } else if (expression instanceof InExpression) {
            return map(((InExpression) expression));
        } else {
            throw new RuntimeException("Unsupported expression: " + expression);
        }
    }

    private Filter map(BinaryExpression binaryExpression) {
        if (binaryExpression instanceof AndExpression) {
            return new And(map(binaryExpression.getLeftExpression()), map(binaryExpression.getRightExpression()));
        } else if (binaryExpression instanceof OrExpression) {
            return new Or(map(binaryExpression.getLeftExpression()), map(binaryExpression.getRightExpression()));
        } else if (binaryExpression instanceof EqualsTo) {
            return new Equal(getKey(binaryExpression), getValue(binaryExpression));
        } else if (binaryExpression instanceof NotEqualsTo) {
            return new NotEqual(getKey(binaryExpression), getValue(binaryExpression));
        } else if (binaryExpression instanceof net.sf.jsqlparser.expression.operators.relational.GreaterThan) {
            return new GreaterThan(getKey(binaryExpression), getValue(binaryExpression));
        } else if (binaryExpression instanceof net.sf.jsqlparser.expression.operators.relational.GreaterThanEquals) {
            return new GreaterThanOrEqual(getKey(binaryExpression), getValue(binaryExpression));
        } else if (binaryExpression instanceof MinorThan) {
            return new LessThan(getKey(binaryExpression), getValue(binaryExpression));
        } else if (binaryExpression instanceof MinorThanEquals) {
            return new LessThanOrEqual(getKey(binaryExpression), getValue(binaryExpression));
        } else {
            throw new RuntimeException("Unsupported expression: " + binaryExpression);
        }
    }

    private Filter map(InExpression inExpression) {
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
            return new NotIn(key, comparisonValues);
        } else {
            return new In(key, comparisonValues);
        }
    }

    private static String getKey(BinaryExpression binaryExpression) {
        return ((Column) binaryExpression.getLeftExpression()).getColumnName();
    }

    private static Comparable<?> getValue(BinaryExpression binaryExpression) {
        Expression expression = binaryExpression.getRightExpression();
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
        }

        throw new IllegalArgumentException("Unsupported expression: " + expression);
    }
}
