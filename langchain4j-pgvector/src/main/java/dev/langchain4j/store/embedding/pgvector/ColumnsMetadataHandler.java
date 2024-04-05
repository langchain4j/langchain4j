package dev.langchain4j.store.embedding.pgvector;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.store.embedding.filter.Filter;

import java.sql.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Handle Metadata stored in independent columns
 */
public class ColumnsMetadataHandler implements MetadataHandler {

    final List<String> columnsDefinition;
    final List<String> columnsName;
    final PgVectorFilterMapper filterMapper;

    final List<String> indexes;

    final String indexType;

    /**
     * MetadataHandler constructor
     * @param config {@link MetadataConfig} configuration
     */
    public ColumnsMetadataHandler(MetadataConfig config) {
        this.columnsDefinition = ensureNotNull(config.definition(), "Metadata definition");
        this.columnsName = config.definition().stream()
                .map(d -> d.trim().split(" ")[0]).collect(Collectors.toList());
        this.filterMapper = new ColumnFilterMapper();
        this.indexes = config.indexes().orElse(Collections.emptyList());
        this.indexType = config.indexType();
    }

    @Override
    public String columnDefinition() {
        return String.join(",", this.columnsDefinition);
    }

    @Override
    public String columnsNames() {
        return String.join(",", this.columnsName);
    }

    @Override
    public Integer nbMetadataColumns() {
        return this.columnsName.size();
    }

    @Override
    public void createMetadataIndexes(Statement statement, String table) {
        String indexTypeSql = indexType == null ? "" : "USING " + indexType;
        this.indexes.stream().map(String::trim)
                .forEach(index -> {
                    String indexSql = String.format("create index if not exists %s_%s on %s %s ( %s )",
                            table, index, table, indexTypeSql, index);
                    try {
                        statement.executeUpdate(indexSql);
                    } catch (SQLException e) {
                        throw new RuntimeException(String.format("Cannot create indexes %s: %s", index, e));
                    }
                });
    }

    @Override
    public String insertClause() {
        return this.columnsName.stream().map(c -> String.format("%s = EXCLUDED.%s", c, c))
                .collect(Collectors.joining(","));
    }

    @Override
    public void setMetadata(PreparedStatement upsertStmt, Integer parameterInitialIndex, Metadata metadata) {
        int i = 0;
        // only column names fields will be stored
        for (String c : this.columnsName) {
            try {
                upsertStmt.setObject(parameterInitialIndex + i, metadata.get(c), Types.OTHER);
                i++;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public String whereClause(Filter filter) {
        return filterMapper.map(filter);
    }

    @Override
    public Metadata fromResultSet(ResultSet resultSet) {
        try {
            Map<String, Object> metadataMap = new HashMap<>();
            for (String c : this.columnsName) {
                if (resultSet.getObject(c) != null) {
                    metadataMap.put(c, resultSet.getObject(c));
                }
            }
            return new Metadata(metadataMap);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

}
