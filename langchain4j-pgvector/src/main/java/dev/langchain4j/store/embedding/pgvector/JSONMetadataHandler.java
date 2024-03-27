package dev.langchain4j.store.embedding.pgvector;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.store.embedding.filter.Filter;

import java.lang.reflect.Type;
import java.sql.*;
import java.util.*;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * This class handle JSON and JSONB Filter mapping
 */
public class JSONMetadataHandler implements MetadataHandler {
    private static final Gson GSON = new Gson();

    private static final Type TYPE = new TypeToken<Map<String, String>>() {}.getType();
    final String columnDefinition;
    final String columnName;
    final JSONFilterMapper filterMapper;

    final List<String> indexes;

    /**
     * MetadataHandler constructor
     * @param config {@link MetadataConfig} configuration
     */
    public JSONMetadataHandler(MetadataConfig config) {
        this.columnDefinition = ensureNotNull(config.definition(), "Metadata definition").get(0);
        if (config.definition().size()>1 || this.columnDefinition().contains(",")) {
            throw new RuntimeException("Multiple columns definition are not allowed in JSON, JSONB Type");
        }
        this.columnName = this.columnDefinition.split(" ")[0];
        this.filterMapper = new JSONFilterMapper(columnName);
        this.indexes = config.indexes().orElse(Collections.emptyList());
    }

    @Override
    public String columnDefinition() {
        return columnDefinition;
    }

    @Override
    public String columnsNames() {
        return this.columnName;
    }

    @Override
    public void createMetadataIndexes(Statement statement, String table) {
        if (this.indexes != null && !this.indexes.isEmpty()) {
            throw new RuntimeException("Indexes are not allowed for JSON metadata, use JSONB instead");
        }
    }

    @Override
    public String whereClause(Filter filter) {
        return filterMapper.map(filter);
    }

    @Override
    public Metadata fromResultSet(ResultSet resultSet) {
        try {
            String metadataJson = Optional.ofNullable(resultSet.getString(columnsNames())).orElse("{}");
            Map<String, String> metadataMap = new HashMap<>(GSON.fromJson(metadataJson, TYPE));
            return new Metadata(new HashMap<>(metadataMap));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Integer nbMetadataColumns() {
        return 1;
    }

    @Override
    public String insertClause() {
        return String.format("%s = EXCLUDED.%s", this.columnName, this.columnName);
    }

    @Override
    public void setMetadata(PreparedStatement upsertStmt, Integer parameterInitialIndex, Metadata metadata) {
        try {
            upsertStmt.setObject(parameterInitialIndex, GSON.toJson(metadata.asMap()), Types.OTHER);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
