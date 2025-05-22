package dev.langchain4j.store.embedding.oceanbase.search;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.filter.Filter;

/**
 * Template for the search operation.
 * Implements the Template Method pattern to define the skeleton of the search algorithm,
 * deferring some steps to subclasses.
 *
 * @param <T> The type of embedded objects
 */
public abstract class SearchTemplate<T> {
    
    /**
     * The main template method that defines the search algorithm.
     * 
     * @param dataSource The data source to use
     * @param request The search request
     * @return The search result
     */
    public final EmbeddingSearchResult<T> search(DataSource dataSource, EmbeddingSearchRequest request) {
        validateRequest(request);
        
        if (request.maxResults() <= 0) {
            return new EmbeddingSearchResult<>(new ArrayList<>());
        }
        
        String query = buildSearchQuery(request);
        
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            
            setParameters(statement, request);
            
            try (ResultSet resultSet = statement.executeQuery()) {
                List<EmbeddingMatch<T>> matches = processResults(resultSet, request);
                return new EmbeddingSearchResult<>(matches);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error executing search query: " + e.getMessage(), e);
        }
    }
    
    /**
     * Validates the search request.
     * This is a hook that may be overridden by subclasses.
     * 
     * @param request The search request to validate
     */
    protected void validateRequest(EmbeddingSearchRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Search request cannot be null");
        }
        if (request.queryEmbedding() == null) {
            throw new IllegalArgumentException("Query embedding cannot be null");
        }
    }
    
    /**
     * Builds the search query.
     * This is an abstract method that must be implemented by subclasses.
     * 
     * @param request The search request
     * @return The SQL query string
     */
    protected abstract String buildSearchQuery(EmbeddingSearchRequest request);
    
    /**
     * Sets the parameters for the prepared statement.
     * This is an abstract method that must be implemented by subclasses.
     * 
     * @param statement The prepared statement
     * @param request The search request
     * @throws SQLException If an error occurs setting the parameters
     */
    protected abstract void setParameters(PreparedStatement statement, EmbeddingSearchRequest request) throws SQLException;
    
    /**
     * Processes the result set and creates embedding matches.
     * This is an abstract method that must be implemented by subclasses.
     * 
     * @param resultSet The result set from the query
     * @param request The search request
     * @return A list of embedding matches
     * @throws SQLException If an error occurs processing the result set
     */
    protected abstract List<EmbeddingMatch<T>> processResults(ResultSet resultSet, EmbeddingSearchRequest request) throws SQLException;
    
    /**
     * Converts a filter to a WHERE clause.
     * This is a hook that may be overridden by subclasses.
     * 
     * @param filter The filter to convert
     * @return The WHERE clause, or null if no filter is applied
     */
    protected String buildWhereClause(Filter filter) {
        return null;
    }
}
