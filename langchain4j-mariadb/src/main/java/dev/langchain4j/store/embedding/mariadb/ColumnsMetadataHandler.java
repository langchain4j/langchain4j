package dev.langchain4j.store.embedding.mariadb;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.store.embedding.filter.Filter;
import java.sql.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Handle Metadata stored in independent columns
 */
class ColumnsMetadataHandler implements MetadataHandler {

    private final List<MetadataColumDefinition> columnsDefinition;
    private final List<String> columnsName;
    private final List<String> escapedColumnsName;
    private final String insertClause;

    private final MariaDbFilterMapper filterMapper;
    private final List<String> indexes;

    /**
     * MetadataHandler constructor
     * @param config {@link MetadataStorageConfig} configuration
     */
    public ColumnsMetadataHandler(MetadataStorageConfig config, List<String> sqlKeywords) {
        List<String> columnsDefinitionList = ensureNotEmpty(config.columnDefinitions(), "Metadata definition");
        this.filterMapper = new ColumnFilterMapper();

        // enquote identifier if keywords
        this.indexes = ((List<String>) getOrDefault(config.indexes(), Collections.emptyList()))
                .stream()
                        .map(key -> {
                            if (sqlKeywords.contains(key.toLowerCase(Locale.ROOT))) {
                                try {
                                    return org.mariadb.jdbc.Driver.enquoteIdentifier(key, true);
                                } catch (SQLException e) {
                                    // eat
                                }
                            }
                            return key;
                        })
                        .toList();
        this.columnsDefinition = columnsDefinitionList.stream()
                .map(str -> MetadataColumDefinition.from(str, sqlKeywords))
                .collect(Collectors.toList());
        this.escapedColumnsName = columnsDefinition.stream()
                .map(MetadataColumDefinition::escapedName)
                .collect(Collectors.toList());
        this.insertClause = ", "
                + this.escapedColumnsName.stream()
                        .map(c -> String.format("%s = VALUES(%s)", c, c))
                        .collect(Collectors.joining(","));
        this.columnsName =
                columnsDefinition.stream().map(MetadataColumDefinition::name).collect(Collectors.toList());
    }

    @Override
    public String columnDefinitionsString() {
        return this.columnsDefinition.stream()
                .map(MetadataColumDefinition::fullDefinition)
                .collect(Collectors.joining(","));
    }

    @Override
    public List<String> escapedColumnsName() {
        return this.escapedColumnsName;
    }

    @Override
    public void createMetadataIndexes(Statement statement, String table) {
        StringBuilder sb = new StringBuilder();
        this.indexes.stream().map(String::trim).forEach(index -> {
            sb.append(",").append(MariaDbValidator.validateAndEnquoteIdentifier(index, false));
        });
        String indexFields = sb.toString().substring(1);
        String indexSql = "create index if not exists %s on %s ( %s )"
                .formatted((table + "_metadata_idx").replaceAll("[ \\`\"'\\\\\\P{Print}]", ""), table, indexFields);
        try {
            statement.executeUpdate(indexSql);
        } catch (SQLException e) {
            throw new RuntimeException(String.format("Cannot create indexes on %s: %s", indexFields, e));
        }
    }

    @Override
    public String insertClause() {
        return insertClause;
    }

    @Override
    public void setMetadata(PreparedStatement upsertStmt, Integer parameterInitialIndex, Metadata metadata) {
        int i = 0;
        // only column names fields will be stored
        Map<String, Object> meta = metadata.toMap();
        for (String c : this.columnsName) {
            try {
                upsertStmt.setObject(parameterInitialIndex + i, meta.get(c));
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
