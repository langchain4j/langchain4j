package dev.langchain4j.store.embedding.oceanbase.sql;

import java.util.function.BiFunction;

import dev.langchain4j.store.embedding.filter.Filter;

/**
 * Factory for creating SQL filters.
 * Implements the Factory Method pattern.
 */
public class SQLFilterFactory {
    
    /**
     * Creates a SQLFilter from a Filter and key mapper.
     * 
     * @param filter The filter to convert
     * @param keyMapper Function that maps a key and value to a SQL column expression
     * @return A SQLFilter representing the given filter
     */
    public static SQLFilter create(Filter filter, BiFunction<String, Object, String> keyMapper) {
        if (filter == null) {
            return SQLFilters.matchAllRows();
        }
        
        SQLFilterVisitor visitor = new SQLFilterVisitor(keyMapper);
        return visitor.process(filter);
    }
}
