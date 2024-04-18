package dev.langchain4j.store.embedding.pgvector;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.internal.Json;
import dev.langchain4j.store.embedding.filter.Filter;

import java.sql.*;
import java.util.*;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.internal.Utils.getOrDefault;

/**
 * This class handle JSON and JSONB Filter mapping
 */
class JSONMetadataHandler implements MetadataHandler {

    final String columnDefinition;
    final String columnName;
    final JSONFilterMapper filterMapper;
    final List<String> indexes;

    /**
     * MetadataHandler constructor
     * @param config {@link MetadataStorageConfig} configuration
     */
    public JSONMetadataHandler(MetadataStorageConfig config) {
        this.columnDefinition = ensureNotNull(config.definition(), "Metadata definition").get(0);
        if (config.definition().size()>1 || this.columnDefinition().contains(",")) {
            throw new RuntimeException("Multiple columns definition are not allowed in JSON, JSONB Type");
        }
        this.columnName = this.columnDefinition.split(" ")[0];
        this.filterMapper = new JSONFilterMapper(columnName);
        this.indexes = getOrDefault(config.indexes(), Collections.emptyList());
    }

    @Override
    public String columnDefinition() {
        return columnDefinition;
    }

    @Override
    public List<String> columnsNames() {
        return Collections.singletonList(this.columnName);
    }

    @Override
    public void createMetadataIndexes(Statement statement, String table) {
        if (!this.indexes.isEmpty()) {
            throw new RuntimeException("Indexes are not allowed for JSON metadata, use JSONB instead");
        }
    }

    @Override
    public String whereClause(Filter filter) {
        return filterMapper.map(filter);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Metadata fromResultSet(ResultSet resultSet) {
        try {
            String metadataJson = getOrDefault(resultSet.getString(columnsNames().get(0)),"{}");
            return new Metadata(Json.fromJson(metadataJson, Map.class));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String insertClause() {
        return String.format("%s = EXCLUDED.%s", this.columnName, this.columnName);
    }

    @Override
    public void setMetadata(PreparedStatement upsertStmt, Integer parameterInitialIndex, Metadata metadata) {
        try {
            upsertStmt.setObject(parameterInitialIndex, Json.toJson(metadata.asMap()), Types.OTHER);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
