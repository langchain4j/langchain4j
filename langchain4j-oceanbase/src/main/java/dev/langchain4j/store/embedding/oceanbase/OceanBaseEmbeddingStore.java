package dev.langchain4j.store.embedding.oceanbase;

import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.oceanbase.search.OceanBaseSearchTemplate;
import dev.langchain4j.store.embedding.oceanbase.search.SearchTemplate;

import dev.langchain4j.store.embedding.oceanbase.sql.SQLFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;
import javax.sql.DataSource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * OceanBase Vector EmbeddingStore Implementation
 */
public final class OceanBaseEmbeddingStore implements EmbeddingStore<TextSegment> {

    private static final Logger log = LoggerFactory.getLogger(OceanBaseEmbeddingStore.class);

    /**
     * DataSource configured to connect with an OceanBase Database.
     */
    private final DataSource dataSource;

    /**
     * Table where embeddings are stored.
     */
    private final EmbeddingTable table;

    /**
     * <code>true</code> if search should use an exact search, or <code>false</code> if
     * it should use approximate search.
     */
    private final boolean isExactSearch;
    
    /**
     * JSON Object Mapper for metadata serialization/deserialization
     */
    private final ObjectMapper objectMapper;
    
    /**
     * Search template for vector search operations
     */
    private final SearchTemplate<TextSegment> searchTemplate;

    /**
     * Constructs embedding store configured by a builder.
     *
     * @param builder Builder that configures the embedding store. Not null.
     * @throws IllegalArgumentException If the configuration is not valid.
     */
    private OceanBaseEmbeddingStore(Builder builder) {
        dataSource = builder.dataSource;
        table = builder.embeddingTable;
        isExactSearch = builder.isExactSearch;
        objectMapper = new ObjectMapper();
        searchTemplate = new OceanBaseSearchTemplate(table, isExactSearch);

        try {
            table.create(dataSource);
        } catch (SQLException sqlException) {
            throw uncheckSQLException(sqlException);
        }
    }

    /**
     * Transforms a SQLException into a RuntimeException.
     * @param sqlException SQLException to transform. Not null.
     * @return A RuntimeException that wraps the SQLException.
     */
    private static RuntimeException uncheckSQLException(SQLException sqlException) {
        return new RuntimeException("SQL error: " + sqlException.getMessage(), sqlException);
    }

    /**
     * Returns a null-safe item from a List.
     * @param list List to get an item from.
     * @param index Index of the item to get.
     * @param name Name of the parameter, for error messages.
     * @param <T> Type of the list items.
     * @return The item at the given index.
     * @throws IllegalArgumentException If the item is null.
     */
    private static <T> T ensureIndexNotNull(List<T> list, int index, String name) {
        if (index < 0 || index >= list.size()) {
            throw new IllegalArgumentException(String.format(
                    "Index %d is out of bounds for %s list of size %d", index, name, list.size()));
        }
        T item = list.get(index);
        if (item == null) {
            throw new IllegalArgumentException(String.format("%s[%d] is null", name, index));
        }
        return item;
    }

    private static String ensureNotEmpty(String value, String name) {
        ensureNotNull(value, name);
        if (value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " cannot be empty");
        }
        return value;
    }

    @Override
    public String add(Embedding embedding) {
        ensureNotNull(embedding, "embedding");
        
        String id = randomUUID();
        add(id, embedding);
        return id;
    }

    @Override
    public void add(String id, Embedding embedding) {
        ensureNotEmpty(id, "id");
        ensureNotNull(embedding, "embedding");
        
        String sql = "INSERT INTO " + table.name() + " (" + 
                table.idColumn() + ", " + 
                table.embeddingColumn() + 
                ") VALUES (?, ?)";
        
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            statement.setString(2, vectorToString(embedding.vector()));
            statement.executeUpdate();
        } catch (SQLException sqlException) {
            throw uncheckSQLException(sqlException);
        }
    }
    
    @Override
    public List<String> generateIds(final int n) {
        List<String> ids = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            ids.add(randomUUID());
        }
        return ids;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        ensureNotNull(embeddings, "embeddings");
        
        if (embeddings.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<String> ids = generateIds(embeddings.size());
        
        String sql = "INSERT INTO " + table.name() + " (" + 
                table.idColumn() + ", " + 
                table.embeddingColumn() + 
                ") VALUES (?, ?)";
        
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            
            for (int i = 0; i < ids.size(); i++) {
                statement.setString(1, ids.get(i));
                statement.setString(2, vectorToString(embeddings.get(i).vector()));
                statement.addBatch();
            }
            
            statement.executeBatch();
            return ids;
        } catch (SQLException sqlException) {
            throw uncheckSQLException(sqlException);
        }
    }

    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        ensureNotNull(embedding, "embedding");
        ensureNotNull(textSegment, "textSegment");
        
        String id = randomUUID();
        
        String sql = "INSERT INTO " + table.name() + " (" + 
                table.idColumn() + ", " + 
                table.embeddingColumn() + ", " + 
                table.textColumn() + ", " + 
                table.metadataColumn() + 
                ") VALUES (?, ?, ?, ?)";
        
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            statement.setString(2, vectorToString(embedding.vector()));
            statement.setString(3, textSegment.text());
            statement.setString(4, metadataToJson(textSegment.metadata()));
            statement.executeUpdate();
            return id;
        } catch (SQLException sqlException) {
            throw uncheckSQLException(sqlException);
        }
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> textSegments) {
        ensureNotNull(embeddings, "embeddings");
        ensureNotNull(textSegments, "textSegments");
        
        if (embeddings.isEmpty()) {
            return Collections.emptyList();
        }
        
        if (embeddings.size() != textSegments.size()) {
            throw new IllegalArgumentException(String.format(
                    "Number of embeddings (%d) and text segments (%d) must be the same",
                    embeddings.size(), textSegments.size()));
        }
        
        List<String> ids = generateIds(embeddings.size());
        addAll(ids, embeddings, textSegments);
        return ids;
    }

    @Override
    public void addAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        ensureNotNull(ids, "ids");
        ensureNotNull(embeddings, "embeddings");
        ensureNotNull(embedded, "embedded");
        
        if (ids.isEmpty() || embeddings.isEmpty() || embedded.isEmpty()) {
            return;
        }
        
        if (ids.size() != embeddings.size() || ids.size() != embedded.size()) {
            throw new IllegalArgumentException(String.format(
                    "Number of ids (%d), embeddings (%d), and embedded objects (%d) must be the same",
                    ids.size(), embeddings.size(), embedded.size()));
        }
        
        String sql = "INSERT INTO " + table.name() + " (" + 
                table.idColumn() + ", " + 
                table.embeddingColumn() + ", " + 
                table.textColumn() + ", " + 
                table.metadataColumn() + 
                ") VALUES (?, ?, ?, ?)";
        
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            
            for (int i = 0; i < ids.size(); i++) {
                String id = ensureIndexNotNull(ids, i, "ids");
                Embedding embedding = ensureIndexNotNull(embeddings, i, "embeddings");
                TextSegment segment = ensureIndexNotNull(embedded, i, "embedded");
                
                statement.setString(1, id);
                statement.setString(2, vectorToString(embedding.vector()));
                statement.setString(3, segment.text());
                statement.setString(4, metadataToJson(segment.metadata()));
                statement.addBatch();
            }
            
            statement.executeBatch();
        } catch (SQLException sqlException) {
            throw uncheckSQLException(sqlException);
        }
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        // Use the search template (Template Method pattern)
        return searchTemplate.search(dataSource, request);
    }

    @Override
    public void remove(String id) {
        ensureNotNull(id, "id");
        
        String sql = "DELETE FROM " + table.name() + " WHERE " + table.idColumn() + " = ?";
        
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            statement.executeUpdate();
        } catch (SQLException sqlException) {
            throw uncheckSQLException(sqlException);
        }
    }

    @Override
    public void removeAll(Collection<String> ids) {
        ensureNotNull(ids, "ids");
        
        if (ids.isEmpty()) {
            return;
        }
        
        // Use placeholders for each ID in the IN clause
        String placeholders = String.join(", ", Collections.nCopies(ids.size(), "?"));
        String sql = "DELETE FROM " + table.name() + " WHERE " + table.idColumn() + " IN (" + placeholders + ")";
        
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            
            int index = 1;
            for (String id : ids) {
                statement.setString(index++, id);
            }
            
            statement.executeUpdate();
        } catch (SQLException sqlException) {
            throw uncheckSQLException(sqlException);
        }
    }

    @Override
    public void removeAll(Filter filter) {
        ensureNotNull(filter, "filter");
        
        SQLFilter sqlFilter = dev.langchain4j.store.embedding.oceanbase.sql.SQLFilterFactory.create(
                filter, (key, value) -> table.mapMetadataKey(key));
        
        if (sqlFilter.matchesNoRows()) {
            return;
        }
        
        String sql;
        if (sqlFilter.matchesAllRows()) {
            sql = "DELETE FROM " + table.name();
        } else {
            sql = "DELETE FROM " + table.name() + " WHERE " + sqlFilter.toSql();
        }
        
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        } catch (SQLException sqlException) {
            throw uncheckSQLException(sqlException);
        }
    }

    @Override
    public void removeAll() {
        String sql = "DELETE FROM " + table.name();
        
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        } catch (SQLException sqlException) {
            throw uncheckSQLException(sqlException);
        }
    }

    /**
     * Converts a vector to a string representation.
     * 
     * @param vector The vector to convert.
     * @return String representation of the vector in OceanBase format.
     */
    private String vectorToString(float[] vector) {
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
     * Converts Metadata to a JSON string.
     * 
     * @param metadata The metadata to convert.
     * @return JSON string representation of the metadata.
     */
    private String metadataToJson(Metadata metadata) {
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
     * Returns a builder for configuring an OceanBaseEmbeddingStore.
     *
     * @param dataSource DataSource that connects to an OceanBase Database.
     * @return A builder.
     */
    public static Builder builder(DataSource dataSource) {
        return new Builder(dataSource);
    }

    /**
     * Builder for an OceanBaseEmbeddingStore.
     */
    public static final class Builder {

        private final DataSource dataSource;
        private EmbeddingTable embeddingTable;
        private boolean isExactSearch = false;

        /**
         * Constructs a builder for an OceanBaseEmbeddingStore.
         *
         * @param dataSource DataSource that connects to an OceanBase Database.
         * @throws IllegalArgumentException If the DataSource is null.
         */
        private Builder(DataSource dataSource) {
            ensureNotNull(dataSource, "dataSource");
            this.dataSource = dataSource;
        }

        /**
         * Sets the embedding table with a default table configuration.
         *
         * @param tableName Name of the embedding table.
         * @return This builder.
         * @throws IllegalArgumentException If the table name is null.
         */
        public Builder embeddingTable(String tableName) {
            ensureNotNull(tableName, "tableName");
            this.embeddingTable = EmbeddingTable.builder(tableName).build();
            return this;
        }

        /**
         * Sets the embedding table with a default table configuration and a create option.
         *
         * @param tableName Name of the embedding table.
         * @param createOption Option for table creation.
         * @return This builder.
         * @throws IllegalArgumentException If the table name or create option is null.
         */
        public Builder embeddingTable(String tableName, CreateOption createOption) {
            ensureNotNull(tableName, "tableName");
            ensureNotNull(createOption, "createOption");
            this.embeddingTable = EmbeddingTable.builder(tableName)
                    .createOption(createOption)
                    .build();
            return this;
        }

        /**
         * Sets the embedding table with a custom configuration.
         *
         * @param embeddingTable Embedding table configuration.
         * @return This builder.
         * @throws IllegalArgumentException If the embedding table is null.
         */
        public Builder embeddingTable(EmbeddingTable embeddingTable) {
            ensureNotNull(embeddingTable, "embeddingTable");
            this.embeddingTable = embeddingTable;
            return this;
        }

        /**
         * Sets whether to use exact search instead of approximate search.
         *
         * @param isExactSearch true to use exact search, false to use approximate search.
         * @return This builder.
         */
        public Builder exactSearch(boolean isExactSearch) {
            this.isExactSearch = isExactSearch;
            return this;
        }

        /**
         * Builds an OceanBaseEmbeddingStore with the configuration defined by this builder.
         *
         * @return A new OceanBaseEmbeddingStore.
         * @throws IllegalArgumentException If required parameters are missing.
         */
        public OceanBaseEmbeddingStore build() {
            if (embeddingTable == null) {
                throw new IllegalArgumentException("embeddingTable must be configured");
            }

            return new OceanBaseEmbeddingStore(this);
        }
    }
}
