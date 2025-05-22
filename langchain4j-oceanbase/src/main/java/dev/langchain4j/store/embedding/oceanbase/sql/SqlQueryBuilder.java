package dev.langchain4j.store.embedding.oceanbase.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Builder for SQL queries.
 * Implements the Builder pattern to create SQL queries in a fluent, readable way.
 */
public class SqlQueryBuilder {
    private StringBuilder queryBuilder = new StringBuilder();
    private List<String> selectColumns = new ArrayList<>();
    private String fromTable;
    private String whereClause;
    private List<String> orderByColumns = new ArrayList<>();
    private boolean approximate = false;
    private Integer limit;
    
    /**
     * Start building a SELECT query.
     * 
     * @return this builder
     */
    public static SqlQueryBuilder select() {
        return new SqlQueryBuilder();
    }
    
    /**
     * Add columns to the SELECT clause.
     * 
     * @param columns The columns to select
     * @return this builder
     */
    public SqlQueryBuilder columns(String... columns) {
        for (String column : columns) {
            selectColumns.add(column);
        }
        return this;
    }
    
    /**
     * Add a function-based column to the SELECT clause.
     * 
     * @param functionName The name of the function
     * @param args The function arguments
     * @return this builder
     */
    public SqlQueryBuilder function(String functionName, String... args) {
        String column = functionName + "(" + String.join(", ", args) + ")";
        selectColumns.add(column);
        return this;
    }
    
    /**
     * Add an alias to the last added column.
     * 
     * @param alias The alias name
     * @return this builder
     */
    public SqlQueryBuilder as(String alias) {
        if (!selectColumns.isEmpty()) {
            int lastIndex = selectColumns.size() - 1;
            String column = selectColumns.get(lastIndex) + " AS " + alias;
            selectColumns.set(lastIndex, column);
        }
        return this;
    }
    
    /**
     * Set the FROM clause.
     * 
     * @param table The table name
     * @return this builder
     */
    public SqlQueryBuilder from(String table) {
        this.fromTable = table;
        return this;
    }
    
    /**
     * Set the WHERE clause.
     * 
     * @param condition The WHERE condition
     * @return this builder
     */
    public SqlQueryBuilder where(String condition) {
        this.whereClause = condition;
        return this;
    }
    
    /**
     * Add an ORDER BY clause.
     * 
     * @param columns The columns to order by
     * @return this builder
     */
    public SqlQueryBuilder orderBy(String... columns) {
        for (String column : columns) {
            orderByColumns.add(column);
        }
        return this;
    }
    
    /**
     * Add an ORDER BY clause with a function.
     * 
     * @param functionName The name of the function
     * @param args The function arguments
     * @return this builder
     */
    public SqlQueryBuilder orderByFunction(String functionName, String... args) {
        String column = functionName + "(" + String.join(", ", args) + ")";
        orderByColumns.add(column);
        return this;
    }
    
    /**
     * Use approximate search (for vector search in OceanBase).
     * 
     * @return this builder
     */
    public SqlQueryBuilder approximate() {
        this.approximate = true;
        return this;
    }
    
    /**
     * Set the LIMIT clause.
     * 
     * @param limit The maximum number of rows to return
     * @return this builder
     */
    public SqlQueryBuilder limit(int limit) {
        this.limit = limit;
        return this;
    }
    
    /**
     * Build the SQL query string.
     * 
     * @return The complete SQL query
     */
    public String build() {
        if (selectColumns.isEmpty()) {
            throw new IllegalStateException("No columns specified for SELECT");
        }
        if (fromTable == null) {
            throw new IllegalStateException("No table specified for FROM");
        }
        
        queryBuilder.append("SELECT ").append(String.join(", ", selectColumns));
        queryBuilder.append(" FROM ").append(fromTable);
        
        if (whereClause != null && !whereClause.isEmpty()) {
            queryBuilder.append(" WHERE ").append(whereClause);
        }
        
        if (!orderByColumns.isEmpty()) {
            queryBuilder.append(" ORDER BY ").append(String.join(", ", orderByColumns));
            
            // Add APPROXIMATE keyword if needed
            if (approximate) {
                queryBuilder.append(" APPROXIMATE");
            }
        }
        
        if (limit != null) {
            queryBuilder.append(" LIMIT ").append(limit);
        }
        
        return queryBuilder.toString();
    }
}
