package dev.langchain4j.store.embedding.oracle;

/**
 * <p>
 * Abstract class extended by {@link IVFIndex.Builder} and {@link JSONIndex.Builder}.
 * </p><p>
 * It contains helper methods used by IndexBuilder implementations and describes the
 * {@link IndexBuilder#build(EmbeddingTable)} method that will be called when the
 * OracleEmbeddingStore is created to create the index.
 * </p>
 */
public abstract class IndexBuilder {
  String indexName;
  private static final int INDEX_NAME_MAX_LENGTH = 128;

  /**
   * Builds and index given an {@link EmbeddingTable}.
   * @param embeddingTable The embedding table.
   * @return Returns the index described by the {@link IndexBuilder}
   */
  abstract Index build(EmbeddingTable embeddingTable);

  /**
   * Creates an index name given the table name and a suffix.
   * @param tableName The table name.
   * @param suffix The index suffix.
   * @return The index name.
   */
  String buildIndexName(String tableName, String suffix) {
    boolean isQuoted =  tableName.startsWith("\"") && tableName.endsWith("\"");
    // If the table name is a quoted identifier, then the index name must also be quoted.
    if (isQuoted) {
      tableName = unquoteTableName(tableName);
    }
    indexName = truncateIndexName(tableName + suffix, isQuoted);
    if (isQuoted) {
      indexName = "\"" + indexName + "\"";
    }
    return indexName;
  }

  private String truncateIndexName(String indexName, boolean isQuoted) {
    int maxLength = isQuoted ? INDEX_NAME_MAX_LENGTH - 2 : INDEX_NAME_MAX_LENGTH;
    if (indexName.length() > maxLength) {
      indexName = indexName.substring(0, maxLength);
    }
    return indexName;
  }

  private String unquoteTableName(String tableName) {
    return tableName.substring(1, tableName.length() - 1);
  }
}
