package dev.langchain4j.store.embedding.pgvector;

import java.sql.SQLException;
import java.sql.Statement;

/**
 *  Handle metadata as JSONB column.
 */
class JSONBMetadataHandler extends JSONMetadataHandler {

    final String indexType;

    /**
     * MetadataHandler constructor
     * @param config {@link MetadataStorageConfig} configuration
     */
    public JSONBMetadataHandler(MetadataStorageConfig config) {
        super(config);
        if (!this.columnDefinition.getType().equals("jsonb")) {
            throw new RuntimeException("Your column definition type should be JSONB");
        }
        indexType = config.indexType();
    }

    @Override
    public void createMetadataIndexes(Statement statement, String table) {
        String indexTypeSql = indexType == null ? "" : "USING " + indexType;
        for (String str : this.indexes) {
            String index = str.trim();
            String indexName = formatIndex(index);
            try {
                String indexSql = String.format("create index if not exists %s_%s on %s %s (%s)",
                        table, indexName, table, indexTypeSql, index);
                statement.executeUpdate(indexSql);
            } catch (SQLException e) {
                throw new RuntimeException(String.format("Cannot create index %s: %s", index, e));
            }
        }
    }

    String formatIndex(String index) {
        // (metadata_b->'name')
        String indexName;
        if (index.contains("->")) {
            indexName = columnName + "_" + index.substring(index.indexOf("->") + 2, index.length() - 1)
                    .trim().replaceAll("'", "");
        } else {
            indexName = index.replaceAll(" ", "_");
        }
        return indexName;
    }
}
