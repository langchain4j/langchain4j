package dev.langchain4j.store.embedding.filter.sql;

import dev.langchain4j.store.embedding.filter.MetadataFilter;
import dev.langchain4j.store.embedding.filter.MetadataFilterParser;
import dev.langchain4j.store.embedding.filter.comparison.Equal;
import dev.langchain4j.store.embedding.filter.comparison.GreaterThan;
import dev.langchain4j.store.embedding.filter.comparison.LessThan;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.MinorThan;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.PlainSelect;

/**
 * TODO
 */
public class SqlMetadataFilterParser implements MetadataFilterParser {

    /**
     * TODO
     * Can parse full SQL statements (TODO example) as well as just where clause (TODO example)
     *
     * @param sql
     * @return
     */
    @Override
    public MetadataFilter parse(String sql) {

        if (!sql.toUpperCase().startsWith("SELECT")) {
            sql = "SELECT * FROM fake_table WHERE " + sql; // TODO
        }

        try {
            PlainSelect select = (PlainSelect) CCJSqlParserUtil.parse(sql);
            return map(select.getWhere());
        } catch (JSQLParserException e) {
            throw new RuntimeException(e);
        }
    }

    private MetadataFilter map(Expression expression) {
        if (expression instanceof BinaryExpression) {
            return map((BinaryExpression) expression);
        } else if (expression instanceof NotExpression) {
            return new Not(map(((NotExpression) expression).getExpression()));
        } else if (expression instanceof Parenthesis) {
            return map(((Parenthesis) expression).getExpression());
        }
        // TODO more cases
        throw new UnsupportedOperationException("TODO " + expression.getClass());
    }

    private MetadataFilter map(BinaryExpression binaryExpression) {

        if (binaryExpression instanceof AndExpression) {
            return new And(
                    map(binaryExpression.getLeftExpression()),
                    map(binaryExpression.getRightExpression())
            );
        } else if (binaryExpression instanceof OrExpression) {
            return new Or(
                    map(binaryExpression.getLeftExpression()),
                    map(binaryExpression.getRightExpression())
            );
        } else if (binaryExpression instanceof EqualsTo) {
            return new Equal(
                    getKey(binaryExpression),
                    getValue(binaryExpression)
            );
        } else if (binaryExpression instanceof net.sf.jsqlparser.expression.operators.relational.GreaterThan) {
            return new GreaterThan(
                    getKey(binaryExpression),
                    getValue(binaryExpression)
            );
        } else if (binaryExpression instanceof MinorThan) {
            return new LessThan(
                    getKey(binaryExpression),
                    getValue(binaryExpression)
            );
        } else {
            throw new RuntimeException("TODO");
        }
    }

    private static String getKey(BinaryExpression binaryExpression) {
        return ((Column) binaryExpression.getLeftExpression()).getColumnName();
    }

    private static Comparable getValue(BinaryExpression binaryExpression) {
        Expression expression = binaryExpression.getRightExpression();
        if (expression instanceof StringValue) {
            return ((StringValue) expression).getValue();
        } else if (expression instanceof LongValue) {
            return ((LongValue) expression).getValue();
        } else if (expression instanceof DoubleValue) {
            return ((DoubleValue) expression).getValue();
        } else if (expression instanceof Column && (
                (((Column) expression).getColumnName().equalsIgnoreCase("true"))
                        || (((Column) expression).getColumnName().equalsIgnoreCase("false"))
        )) {
            return Boolean.parseBoolean(((Column) expression).getColumnName());
        } else {
            throw new IllegalArgumentException("TODO");
        }
    }
}
