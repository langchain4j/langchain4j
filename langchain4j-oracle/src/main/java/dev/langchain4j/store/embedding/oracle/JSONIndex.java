package dev.langchain4j.store.embedding.oracle;

import oracle.jdbc.OracleType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * <p>
 *   This index type can be used to index the metadata column of the
 *   {@link EmbeddingTable}.
 * </p><p>
 *   It creates a function based index on one or several keys of the JSON document using
 *   the same function used by {@link OracleEmbeddingStore} to filter.
 * </p><p>
 *   The {@link Builder} allows to configure the JSON index.
 * </p>
 */
public class JSONIndex extends Index {

  JSONIndex(CreateOption createOption, String createIndexStatement, String dropIndexStatement) {
    super(createOption, createIndexStatement, dropIndexStatement);
  }

  public static JSONIndex.Builder builder() {
    return new JSONIndex.Builder();
  }

  /**
   * An {@Link IndexBuilder} that builds a JSONIndex.
   */
  public static class Builder extends IndexBuilder {

    /**
     * Indicates whether the index is unique.
     */
    private boolean isUnique;

    /**
     * Indicates whether the index is a bitmap index.
     */
    private boolean isBitmap;

    /**
     * Create option for the index, by default create if not exists;
     */
    private CreateOption createOption = CreateOption.CREATE_IF_NOT_EXISTS;

    /**
     * List of index expressions of the index. An expression is added for
     * each JSON key that is indexed.
     */
    private final List<MetadataKey> indexExpressions = new ArrayList<MetadataKey>();

    /**
     * Use ASC or DESC to indicate whether the index should be created in ascending or
     * descending order. Indexes on character data are created in ascending or descending
     * order of the character values in the database character set.
     */
    public enum Order {
      /**
       * Create the index on ascending order.
       */
      ASC,
      /**
       * Create the index on descending order.
       */
      DESC
    }

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
    public Builder createOption(CreateOption createOption) {
      ensureNotNull(createOption, "createOption");
      this.createOption = createOption;
      return this;
    }

    /**
     * Sets the index name.
     * @param indexName The name of the index.
     * @return This builder.
     */
    public Builder name(String indexName) {
      this.indexName = indexName;
      return this;
    }

    /**
     * Specify UNIQUE to indicate that the value of the column (or columns) upon
     * which the index is based must be unique.
     * Note that you cannot specify both UNIQUE and BITMAP.
     *
     * @param isUnique True if the index should be UNIQUE otherwise false;
     * @return This builder.
     */
    public Builder isUnique(boolean isUnique) {
      this.isUnique = isUnique;
      return this;
    }

    /**
     * Specify BITMAP to indicate that index is to be created with a bitmap for each
     * distinct key, rather than indexing each row separately.
     *
     * @param isBitmap True if the index should be BITMAP otherwise false;
     * @return This builder.
     */
    public Builder isBitmap(boolean isBitmap) {
      this.isBitmap = isBitmap;
      return this;
    }

    /**
     * Adds a column expression to the index expression that allows to index the
     * value of a given key of the JSON column.
     *
     * @param key   The key to index.
     * @param keyType The java class of the metadata column.
     * @param order The order the index should be created in.
     * @return This builder.
     * @throws IllegalArgumentException If the key is null or empty, if the sqlType is null or if the order is null
     */
    public Builder key(String key, Class<?> keyType, Order order) {
      ensureNotBlank(key, "key");
      ensureNotNull(keyType, "sqlType");
      ensureNotNull(order, "order");
      indexExpressions.add(new MetadataKey(key, keyType, order));
      return this;
    }

    public Index build(EmbeddingTable embeddingTable) {
      if (indexName == null) {
        indexName = buildIndexName(
            embeddingTable.name(),
            "_" + UUID.randomUUID().toString().replace("-", "_").toUpperCase());
      }

      String createIndexStatement = "CREATE " +
          (isUnique ? " UNIQUE " : "") +
          (isBitmap ? " BITMAP " : "") +
          " INDEX " + indexName +
          (createOption == CreateOption.CREATE_IF_NOT_EXISTS ? " IF NOT EXISTS " : "") +
          " ON " + embeddingTable.name() +
          "(" + getIndexExpression(embeddingTable) + ")";
      String dropIndexStatement = "DROP INDEX IF EXISTS " + indexName;
      return new JSONIndex(createOption, createIndexStatement, dropIndexStatement);
    }
    private String getIndexExpression(EmbeddingTable embeddingTable) {
      return indexExpressions.stream().map(metadataKey -> {
        OracleType oracleType = SQLFilters.toOracleType(metadataKey.keyType);
        return embeddingTable.mapMetadataKey(metadataKey.key, oracleType) + " " + metadataKey.order;
      }).collect(Collectors.joining(","));
    }

    private class MetadataKey {
      private String key;
      private Class<?> keyType;
      private Order order;

      public MetadataKey(String key, Class<?> keyType, Order order) {
        this.key = key;
        this.keyType = keyType;
        this.order = order;
      }

      public String getKey() {
        return key;
      }

      public Order getOrder() {
        return order;
      }

      public Class<?> getKeyType() {
        return keyType;
      }
    }
  }
}
