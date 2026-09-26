package dev.langchain4j.store.embedding.filter.builder.language;

import dev.langchain4j.store.embedding.filter.Filter;
import java.util.Objects;

/**
 * Result of processing a natural language query, containing both a structured filter
 * and a modified query for semantic search.
 */
public class FilterResult {
    
    private final Filter filter;
    private final String modifiedQuery;
    
    public FilterResult(Filter filter, String modifiedQuery) {
        this.filter = filter;
        this.modifiedQuery = Objects.requireNonNull(modifiedQuery, "modifiedQuery cannot be null");
    }
    
    /**
     * Gets the structured filter to apply to metadata fields.
     * This filter constrains the search to documents matching specific criteria
     * (e.g., publication date, author, category, etc.).
     * 
     * @return the filter, or null if no structured filtering is needed
     */
    public Filter getFilter() {
        return filter;
    }
    
    /**
     * Gets the modified query for semantic/embedding-based search.
     * This query focuses on the semantic content and removes structural constraints
     * that are better handled by filters.
     * 
     * @return the modified query for embedding search
     */
    public String getModifiedQuery() {
        return modifiedQuery;
    }
    
    @Override
    public String toString() {
        return "FilterResult{" +
                "filter=" + filter +
                ", modifiedQuery='" + modifiedQuery + '\'' +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FilterResult that = (FilterResult) o;
        return Objects.equals(filter, that.filter) && 
               Objects.equals(modifiedQuery, that.modifiedQuery);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(filter, modifiedQuery);
    }
}