package dev.langchain4j.store.embedding.oceanbase.search;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.oceanbase.EmbeddingTable;
import dev.langchain4j.store.embedding.oceanbase.sql.SQLFilter;
import dev.langchain4j.store.embedding.oceanbase.distance.DistanceConverter;
import dev.langchain4j.store.embedding.oceanbase.distance.DistanceConverterFactory;
import dev.langchain4j.store.embedding.oceanbase.sql.SQLFilterFactory;
import dev.langchain4j.store.embedding.oceanbase.sql.SqlQueryBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Concrete implementation of SearchTemplate for OceanBase.
 */
public class OceanBaseSearchTemplate extends SearchTemplate<TextSegment> {

    private final EmbeddingTable table;
    private final boolean isExactSearch;
    private final ObjectMapper objectMapper;
    
    /**
     * Creates a new OceanBaseSearchTemplate.
     * 
     * @param table The embedding table configuration
     * @param isExactSearch Whether to use exact search
     */
    public OceanBaseSearchTemplate(EmbeddingTable table, boolean isExactSearch) {
        this.table = table;
        this.isExactSearch = isExactSearch;
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Converts a vector to a string representation.
     * 
     * @param vector The vector to convert
     * @return String representation of the vector in OceanBase format
     */
    protected String vectorToString(float[] vector) {
        if (vector == null || vector.length == 0) {
            return "[]";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }
    
    /**
     * Converts a string representation of a vector back to a float array.
     * 
     * @param vectorStr The string representation of the vector
     * @return The vector as a float array
     */
    protected float[] stringToVector(String vectorStr) {
        if (vectorStr == null || vectorStr.isEmpty() || vectorStr.equals("[]")) {
            return new float[0];
        }
        
        String trimmed = vectorStr.trim();
        if (trimmed.startsWith("[")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.endsWith("]")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        
        String[] parts = trimmed.split(",");
        float[] result = new float[parts.length];
        
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i].trim());
        }
        
        return result;
    }
    
    /**
     * Converts Metadata to a JSON string.
     * 
     * @param metadata The metadata to convert
     * @return JSON string representation of the metadata
     */
    protected String metadataToJson(Metadata metadata) {
        if (metadata == null || metadata.toMap().isEmpty()) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(metadata.toMap());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to convert metadata to JSON", e);
        }
    }
    
    /**
     * Converts a JSON string to Metadata.
     * 
     * @param json The JSON string to convert
     * @return Metadata object
     */
    @SuppressWarnings("unchecked")
    protected Metadata jsonToMetadata(String json) {
        if (json == null || json.isEmpty()) {
            return Metadata.from(java.util.Collections.emptyMap());
        }

        try {
            Map<String, Object> map = objectMapper.readValue(json, java.util.Map.class);
            return Metadata.from(map);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to convert JSON to metadata", e);
        }
    }

    @Override
    protected String buildSearchQuery(EmbeddingSearchRequest request) {
        // Use SqlQueryBuilder (Builder pattern) to construct the query
        SqlQueryBuilder builder = SqlQueryBuilder.select()
                .columns(table.idColumn(), table.embeddingColumn(), table.textColumn(), table.metadataColumn())
                .function(
                    table.distanceMetric().toLowerCase() + "_distance", 
                    table.embeddingColumn(), 
                    "?"
                )
                .from(table.name());
        
        // Add WHERE clause for filtering
        Filter filter = request.filter();
        if (filter != null) {
            String whereClause = buildWhereClause(filter);
            if (whereClause != null && !whereClause.trim().isEmpty()) {
                builder.where(whereClause);
            }
        }
        
        // Add ORDER BY clause
        builder.orderByFunction(
            table.distanceMetric().toLowerCase() + "_distance",
            table.embeddingColumn(),
            "?"
        );
        
        // Use APPROXIMATE keyword if not exact search
        if (!isExactSearch) {
            builder.approximate();
        }
        
        // Add LIMIT clause
        builder.limit(request.maxResults());
        
        return builder.build();
    }

    @Override
    protected void setParameters(PreparedStatement statement, EmbeddingSearchRequest request) throws SQLException {
        String queryVector = vectorToString(request.queryEmbedding().vector());
        statement.setString(1, queryVector); // For distance calculation in SELECT
        statement.setString(2, queryVector); // For distance calculation in ORDER BY
    }

    @Override
    protected List<EmbeddingMatch<TextSegment>> processResults(ResultSet resultSet, EmbeddingSearchRequest request) throws SQLException {
        List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>();
        
        while (resultSet.next()) {
            String id = resultSet.getString(1);
            String vectorStr = resultSet.getString(2);
            String text = resultSet.getString(3);
            String metadataJson = resultSet.getString(4);
            double distance = resultSet.getDouble(5);
            
            // Convert distance to similarity score using appropriate converter
            DistanceConverter converter = DistanceConverterFactory.getConverter(table.distanceMetric());
            double score = converter.toSimilarity(distance);
            
            // Apply minimum score filter if specified
            if (score < request.minScore()) {
                continue;
            }
            
            Embedding embedding = new Embedding(stringToVector(vectorStr));
            Metadata metadata = jsonToMetadata(metadataJson);
            TextSegment segment = (text != null) ? 
                    TextSegment.from(text, metadata) : 
                    null;
            
            matches.add(new EmbeddingMatch<>(score, id, embedding, segment));
        }
        
        return matches;
    }

    @Override
    protected String buildWhereClause(Filter filter) {
        if (filter == null) {
            return null;
        }
        
        SQLFilter sqlFilter = SQLFilterFactory.create(filter, 
                (key, value) -> table.getMetadataKeyMapper().mapKey(key));
        
        if (sqlFilter.matchesAllRows()) {
            return null;
        }
        
        return sqlFilter.toSql();
    }
}
