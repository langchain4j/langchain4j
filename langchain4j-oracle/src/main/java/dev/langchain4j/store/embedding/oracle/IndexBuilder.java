package dev.langchain4j.store.embedding.oracle;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * <p>
 *   Abstract class that contains common methods for IndexBuilders. Two index
 *   builder implementation exist: {@link IVFIndexBuilder} and {@link
 *   JSONIndexBuilder}. {@link IVFIndexBuilder} can be used to configure an
 *   index on the embedding column of the embedding table and {@link
 *   JSONIndexBuilder} allow to index keys of the metadata column of the
 *   embedding table.
 * </p>
 */
public abstract class IndexBuilder<T extends IndexBuilder> {
  static final int INDEX_NAME_MAX_LENGTH = 128;
  protected String indexName;

  /**
   * CreateOption for the index. By default, the index will not be created.
   */
  CreateOption createOption = CreateOption.DO_NOT_CREATE;

  /**
   * Configures the option to create (or not create) an index. The default is
   * {@link CreateOption#CREATE_IF_NOT_EXISTS}, which means that an index will
   * be created if an index with the same name does not already exist.
   *
   * @param createOption The create option.
   *
   * @return This builder.
   *
   * @throws IllegalArgumentException If createOption is null.
   */
  public T createOption(CreateOption createOption) {
    ensureNotNull(createOption, "createOption");
    this.createOption = createOption;
    return (T) this;
  }

  /**
   * Sets the index name.
   * @param indexName The name of the index.
   * @return This builder.
   */
  public T name(String indexName) {
    this.indexName = indexName;
    return (T) this;
  }

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

  /**
   * Truncates the index name if it os longer that the maximum length allowed
   * by the database.
   * @param indexName The index name.
   * @param isQuoted True if the index name is quoted.
   * @return The index name truncated to the max length allowed by the database.
   */
  private String truncateIndexName(String indexName, boolean isQuoted) {
    int maxLength = isQuoted ? INDEX_NAME_MAX_LENGTH - 2 : INDEX_NAME_MAX_LENGTH;
    if (indexName.length() > maxLength) {
      indexName = indexName.substring(0, maxLength);
    }
    return indexName;
  }

  /**
   * Unquote the table name.
   * @param tableName The table name.
   * @return The unquoted table name.
   */
  private String unquoteTableName(String tableName) {
    return tableName.substring(1, tableName.length() - 1);
  }

  /**
   * Builds the index object configured by this builder.
   * @return The index object.
   */
  public abstract Index build();

  /**
   * Returns the <em>CREATE INDEX</em> SQL statement of the configured index given
   * the embedding table.
   * @param embeddingTable The embedding table.
   * @return The <em>CREATE INDEX</em> SQL statement.
   */
  abstract String getCreateIndexStatement(EmbeddingTable embeddingTable);

  /**
   * Returns the <em>DROP INDEX</em> SQL statement of the configured index given
   * the embedding table.
   * @param embeddingTable The embedding table.
   * @return The <em>DROP INDEX</em> SQL statement.
   */
  String getDropIndexStatement(EmbeddingTable embeddingTable) {

    return "DROP INDEX IF EXISTS " + getIndexName(embeddingTable);
  }

  /**
   * Returns the name of the index, if a name has not been set, the name is generated.
   * @param embeddingTable The embedding table.
   * @return The index name.
   */
  abstract String getIndexName(EmbeddingTable embeddingTable);

}
