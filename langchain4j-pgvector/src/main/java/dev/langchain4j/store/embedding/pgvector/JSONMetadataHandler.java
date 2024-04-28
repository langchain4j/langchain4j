package dev.langchain4j.store.embedding.pgvector;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.internal.Json;
import dev.langchain4j.store.embedding.filter.Filter;

import java.sql.*;
import java.util.*;

import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.Utils.getOrDefault;

/**
 * Handle metadata as JSON column.
 */
class JSONMetadataHandler implements MetadataHandler {

    final MetadataColumDefinition columnDefinition;
    final String columnName;
    final JSONFilterMapper filterMapper;
    final List<String> indexes;

    /**
     * MetadataHandler constructor
     * @param config {@link MetadataStorageConfig} configuration
     */
    public JSONMetadataHandler(MetadataStorageConfig config) {
        List<String> definition = ensureNotEmpty(config.columnDefinitions(), "Metadata definition");
        if (definition.size()>1) {
            throw new IllegalArgumentException("Metadata definition should be an unique column definition, " +
                    "example: metadata JSON NULL");
        }
        this.columnDefinition = MetadataColumDefinition.from(definition.get(0));
        this.columnName = this.columnDefinition.getName();
        this.filterMapper = new JSONFilterMapper(columnName);
        this.indexes = getOrDefault(config.indexes(), Collections.emptyList());
    }

    @Override
    public String columnDefinitionsString() {
        return columnDefinition.getFullDefinition();
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
