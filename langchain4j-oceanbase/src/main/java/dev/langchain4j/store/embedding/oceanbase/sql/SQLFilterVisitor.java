package dev.langchain4j.store.embedding.oceanbase.sql;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThan;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThanOrEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsIn;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThan;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThanOrEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotIn;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;
import dev.langchain4j.store.embedding.oceanbase.sql.SQLFilters.MatchAllSQLFilter;
import dev.langchain4j.store.embedding.oceanbase.sql.SQLFilters.MatchNoSQLFilter;
import dev.langchain4j.store.embedding.oceanbase.sql.SQLFilters.SimpleSQLFilter;

/**
 * Concrete visitor implementation that converts Filter objects to SQLFilter objects.
 * Implements the Visitor pattern.
 */
public class SQLFilterVisitor implements FilterVisitor {
    private final BiFunction<String, Object, String> keyMapper;
    
    /**
     * Creates a new SQLFilterVisitor with the given key mapper.
     * 
     * @param keyMapper Function that maps a key and value to a SQL column expression
     */
    public SQLFilterVisitor(BiFunction<String, Object, String> keyMapper) {
        this.keyMapper = keyMapper;
    }

    /**
     * Helper method to convert a value to its SQL string representation.
     * 
     * @param value The value to convert
     * @return SQL string representation of the value
     */
    private String valueToSql(Object value) {
        if (value == null) {
            return "NULL";
        } else if (value instanceof String) {
            return "'" + ((String) value).replace("'", "''") + "'";
        } else if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        } else {
            return "'" + value.toString().replace("'", "''") + "'";
        }
    }

    @Override
    public SQLFilter visit(IsEqualTo filter) {
        String expr = keyMapper.apply(filter.key(), filter.comparisonValue());
        Object value = filter.comparisonValue();
        
        if (value == null) {
            return new SimpleSQLFilter(expr + " IS NULL");
        } else {
            return new SimpleSQLFilter(expr + " = " + valueToSql(value));
        }
    }

    @Override
    public SQLFilter visit(IsNotEqualTo filter) {
        String expr = keyMapper.apply(filter.key(), filter.comparisonValue());
        Object value = filter.comparisonValue();
        
        if (value == null) {
            return new SimpleSQLFilter(expr + " IS NOT NULL");
        } else {
            return new SimpleSQLFilter("(" + expr + " <> " + valueToSql(value) + " OR " + expr + " IS NULL)");
        }
    }

    @Override
    public SQLFilter visit(IsGreaterThan filter) {
        String expr = keyMapper.apply(filter.key(), filter.comparisonValue());
        return new SimpleSQLFilter(expr + " > " + valueToSql(filter.comparisonValue()));
    }

    @Override
    public SQLFilter visit(IsGreaterThanOrEqualTo filter) {
        String expr = keyMapper.apply(filter.key(), filter.comparisonValue());
        return new SimpleSQLFilter(expr + " >= " + valueToSql(filter.comparisonValue()));
    }

    @Override
    public SQLFilter visit(IsLessThan filter) {
        String expr = keyMapper.apply(filter.key(), filter.comparisonValue());
        return new SimpleSQLFilter(expr + " < " + valueToSql(filter.comparisonValue()));
    }

    @Override
    public SQLFilter visit(IsLessThanOrEqualTo filter) {
        String expr = keyMapper.apply(filter.key(), filter.comparisonValue());
        return new SimpleSQLFilter(expr + " <= " + valueToSql(filter.comparisonValue()));
    }

    @Override
    public SQLFilter visit(IsIn filter) {
        Collection<?> values = filter.comparisonValues();
        if (values == null || values.isEmpty()) {
            return new MatchNoSQLFilter();
        }
        
        List<String> valueStrings = new ArrayList<>(values.size());
        for (Object value : values) {
            valueStrings.add(valueToSql(value));
        }
        
        String expr = keyMapper.apply(filter.key(), values.iterator().next());
        return new SimpleSQLFilter(expr + " IN (" + String.join(", ", valueStrings) + ")");
    }

    @Override
    public SQLFilter visit(IsNotIn filter) {
        Collection<?> values = filter.comparisonValues();
        if (values == null || values.isEmpty()) {
            return new MatchAllSQLFilter();
        }
        
        List<String> valueStrings = new ArrayList<>(values.size());
        for (Object value : values) {
            valueStrings.add(valueToSql(value));
        }
        
        String expr = keyMapper.apply(filter.key(), values.iterator().next());
        return new SimpleSQLFilter("(" + expr + " NOT IN (" + String.join(", ", valueStrings) + ") OR " + expr + " IS NULL)");
    }

    @Override
    public SQLFilter visit(And filter) {
        SQLFilter leftFilter = process(filter.left());
        
        // If left side matches no rows, the entire AND also matches no rows
        if (leftFilter.matchesNoRows()) {
            return new MatchNoSQLFilter();
        }
        
        // If left side matches all rows, the result depends on the right side
        if (leftFilter.matchesAllRows()) {
            return process(filter.right());
        }
        
        SQLFilter rightFilter = process(filter.right());
        
        // If right side matches no rows, the entire AND also matches no rows
        if (rightFilter.matchesNoRows()) {
            return new MatchNoSQLFilter();
        }
        
        // If right side matches all rows, the result depends on the left side
        if (rightFilter.matchesAllRows()) {
            return leftFilter;
        }
        
        // Both sides need to participate in the AND operation
        return new SimpleSQLFilter("(" + leftFilter.toSql() + ") AND (" + rightFilter.toSql() + ")");
    }

    @Override
    public SQLFilter visit(Or filter) {
        SQLFilter leftFilter = process(filter.left());
        
        // If left side matches all rows, the entire OR also matches all rows
        if (leftFilter.matchesAllRows()) {
            return new MatchAllSQLFilter();
        }
        
        // If left side matches no rows, the result depends on the right side
        if (leftFilter.matchesNoRows()) {
            return process(filter.right());
        }
        
        SQLFilter rightFilter = process(filter.right());
        
        // If right side matches all rows, the entire OR also matches all rows
        if (rightFilter.matchesAllRows()) {
            return new MatchAllSQLFilter();
        }
        
        // If right side matches no rows, the result depends on the left side
        if (rightFilter.matchesNoRows()) {
            return leftFilter;
        }
        
        // Both sides need to participate in the OR operation
        return new SimpleSQLFilter("(" + leftFilter.toSql() + ") OR (" + rightFilter.toSql() + ")");
    }

    @Override
    public SQLFilter visit(Not filter) {
        SQLFilter expressionFilter = process(filter.expression());
        
        if (expressionFilter.matchesAllRows()) {
            return new MatchNoSQLFilter();
        } else if (expressionFilter.matchesNoRows()) {
            return new MatchAllSQLFilter();
        } else {
            return new SimpleSQLFilter("NOT (" + expressionFilter.toSql() + ")");
        }
    }
    
    /**
     * Process any Filter object by making it accept this visitor.
     * 
     * @param filter The filter to process
     * @return The resulting SQLFilter
     */
    public SQLFilter process(Filter filter) {
        if (filter instanceof IsEqualTo) {
            return visit((IsEqualTo) filter);
        } else if (filter instanceof IsNotEqualTo) {
            return visit((IsNotEqualTo) filter);
        } else if (filter instanceof IsGreaterThan) {
            return visit((IsGreaterThan) filter);
        } else if (filter instanceof IsGreaterThanOrEqualTo) {
            return visit((IsGreaterThanOrEqualTo) filter);
        } else if (filter instanceof IsLessThan) {
            return visit((IsLessThan) filter);
        } else if (filter instanceof IsLessThanOrEqualTo) {
            return visit((IsLessThanOrEqualTo) filter);
        } else if (filter instanceof IsIn) {
            return visit((IsIn) filter);
        } else if (filter instanceof IsNotIn) {
            return visit((IsNotIn) filter);
        } else if (filter instanceof And) {
            return visit((And) filter);
        } else if (filter instanceof Or) {
            return visit((Or) filter);
        } else if (filter instanceof Not) {
            return visit((Not) filter);
        } else {
            throw new IllegalArgumentException("Unsupported filter type: " + filter.getClass().getName());
        }
    }
}
