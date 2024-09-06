package dev.langchain4j.store.embedding.oracle;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * <p>
 *   Represents a database index. Indexes can be configured using two builders:
 *   <ul>
 *     <li>{@link IVFIndexBuilder}</li>
 *     <li>{@link JSONIndexBuilder}</li>
 *   </ul>
 * </p>
 * <p>
 *   {@link IVFIndexBuilder} allows to configure an Inverted File Flat (IVF) index
 *   on the embedding column of the {@link EmbeddingTable}.
 * </p>
 * <p>
 *   {@link JSONIndexBuilder} allows to configure a function-based index on one or
 *   several keys of the metadata column of the {@link EmbeddingTable}. The function
 *   used to index a key is the same as the function used for searching on the store.
 * </p>
 */
public class Index {

  /**
   * The index builder.
   */
  private IndexBuilder builder;

  /**
   * The name of the table.
   */
  private String tableName;

  /**
   * Create an index.
   * @param builder The builder.
   */
  Index(IndexBuilder builder) {
    this.builder = builder;
  }

  /**
   * Creates a builder to configure an IVF index on the embedding column of
   * the {@link EmbeddingTable}.
   * @return A builder that allows to configure an IVF index.
   */
  public static IVFIndexBuilder ivfIndexBuilder() {
    return new IVFIndexBuilder();
  }

  /**
   * Creates a builder to configure a function-based index on one or several
   * keys of the metadata column of the {@link EmbeddingTable}.
   * @return A builder that allows to configure an index on the metadata
   * column.
   */
  public static JSONIndexBuilder jsonIndexBuilder() {
    return new JSONIndexBuilder();
  }

  /**
   * Returns the name of the index.
   *
   * @return The name of the index or null if the name has not been set and the index
   * has not been created.
   */
  public String name() {
    return builder.indexName;
  }

  /**
   * Returns the name of this table.
   *
   * @return Once the index has been created it returns the table name, otherwise it
   * returns null.
   */
  public String tableName() {
    return tableName;
  }

  /**
   * Creates the index.
   * @param dataSource The datasource.
   * @param embeddingTable The embedding table.
   * @throws SQLException If an error occurs while creating the index.
   */
  void create(DataSource dataSource, EmbeddingTable embeddingTable) throws SQLException {

    ensureNotNull(dataSource,"dataSource");
    ensureNotNull(embeddingTable, "embeddingTable");

    this.tableName = embeddingTable.name();

    if (builder.createOption == CreateOption.DO_NOT_CREATE) return;
    try (Connection connection = dataSource.getConnection();
         Statement statement = connection.createStatement()) {
      if (builder.createOption == CreateOption.CREATE_OR_REPLACE) {
        statement.addBatch(builder.getDropIndexStatement(embeddingTable));
      }
      statement.addBatch(builder.getCreateIndexStatement(embeddingTable));
      statement.executeBatch();
    }
  }

}
