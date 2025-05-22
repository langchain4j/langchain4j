package dev.langchain4j.store.embedding.oceanbase.sql;

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

/**
 * Visitor interface for Filter objects.
 * Implements the Visitor pattern to handle different filter types
 * without using instanceof checks and type casting.
 */
public interface FilterVisitor {
    
    /**
     * Visit an IsEqualTo filter.
     * 
     * @param filter The filter to visit
     * @return The resulting SQL filter
     */
    SQLFilter visit(IsEqualTo filter);
    
    /**
     * Visit an IsNotEqualTo filter.
     * 
     * @param filter The filter to visit
     * @return The resulting SQL filter
     */
    SQLFilter visit(IsNotEqualTo filter);
    
    /**
     * Visit an IsGreaterThan filter.
     * 
     * @param filter The filter to visit
     * @return The resulting SQL filter
     */
    SQLFilter visit(IsGreaterThan filter);
    
    /**
     * Visit an IsGreaterThanOrEqualTo filter.
     * 
     * @param filter The filter to visit
     * @return The resulting SQL filter
     */
    SQLFilter visit(IsGreaterThanOrEqualTo filter);
    
    /**
     * Visit an IsLessThan filter.
     * 
     * @param filter The filter to visit
     * @return The resulting SQL filter
     */
    SQLFilter visit(IsLessThan filter);
    
    /**
     * Visit an IsLessThanOrEqualTo filter.
     * 
     * @param filter The filter to visit
     * @return The resulting SQL filter
     */
    SQLFilter visit(IsLessThanOrEqualTo filter);
    
    /**
     * Visit an IsIn filter.
     * 
     * @param filter The filter to visit
     * @return The resulting SQL filter
     */
    SQLFilter visit(IsIn filter);
    
    /**
     * Visit an IsNotIn filter.
     * 
     * @param filter The filter to visit
     * @return The resulting SQL filter
     */
    SQLFilter visit(IsNotIn filter);
    
    /**
     * Visit an And filter.
     * 
     * @param filter The filter to visit
     * @return The resulting SQL filter
     */
    SQLFilter visit(And filter);
    
    /**
     * Visit an Or filter.
     * 
     * @param filter The filter to visit
     * @return The resulting SQL filter
     */
    SQLFilter visit(Or filter);
    
    /**
     * Visit a Not filter.
     * 
     * @param filter The filter to visit
     * @return The resulting SQL filter
     */
    SQLFilter visit(Not filter);
}
