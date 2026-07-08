package dev.langchain4j.store.embedding.oracle.vecdb;

import dev.langchain4j.store.embedding.oracle.CreateOption;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Prepares the VecDB table and vector index required by an embedding store.
 *
 * <p>This class owns schema lifecycle decisions, while {@link VecDbQueryExecutor} owns the individual JDBC calls to
 * {@code DBMS_VECTOR_DATABASE}. It does not create or close the supplied {@link Connection}.
 *
 * <p>Table lifecycle follows the {@link CreateOption} configured by {@link VecDbEmbeddingTable}:
 * <ul>
 *     <li>{@link CreateOption#CREATE_NONE} requires the table to already exist.</li>
 *     <li>{@link CreateOption#CREATE_IF_NOT_EXISTS} reuses an existing table or creates a missing table.</li>
 *     <li>{@link CreateOption#CREATE_OR_REPLACE} drops an existing table, including its vectors and index, before
 *     creating a replacement.</li>
 * </ul>
 *
 * <p>When a table is created, its index configuration is passed to
 * {@code DBMS_VECTOR_DATABASE.CREATE_VECTOR_TABLE}. A {@code null} index allows VecDB to create its default index. For
 * an existing table, index lifecycle is handled separately using {@code CREATE_INDEX} or {@code REBUILD_INDEX}.
 */
public class VecDbSchemaManager {

    /** Executes the {@code DBMS_VECTOR_DATABASE} operations selected by this manager. */
    private final VecDbQueryExecutor vecDbQueryExecutor;

    /**
     * Creates a schema manager that delegates database operations to the given executor.
     *
     * @param vecDbQueryExecutor executor used for VecDB schema operations
     */
    public VecDbSchemaManager(VecDbQueryExecutor vecDbQueryExecutor) {
        this.vecDbQueryExecutor = vecDbQueryExecutor;
    }

    /**
     * Creates, validates, or replaces a VecDB table and prepares its index according to their configured
     * {@link CreateOption}s.
     *
     * <p>If the table already exists and is reused, the index option is applied independently:
     * <ul>
     *     <li>{@link CreateOption#CREATE_NONE} leaves the existing index unchanged.</li>
     *     <li>{@link CreateOption#CREATE_IF_NOT_EXISTS} creates an index only when one is absent.</li>
     *     <li>{@link CreateOption#CREATE_OR_REPLACE} rebuilds an existing index or creates one when absent.</li>
     * </ul>
     *
     * @param connection active connection to the target Oracle Database; ownership remains with the caller
     * @param embeddingTable VecDB table configuration
     * @param index vector index configuration, or {@code null} to use VecDB defaults for a new table and leave an
     *     existing table's index unchanged
     * @throws SQLException if a {@code DBMS_VECTOR_DATABASE} operation fails
     * @throws IllegalStateException if the table is configured with {@link CreateOption#CREATE_NONE} but does not exist
     * @throws IllegalArgumentException if a missing table is created while its configured index uses
     *     {@link CreateOption#CREATE_NONE}
     */
    void prepareSchema(Connection connection, VecDbEmbeddingTable embeddingTable, VecDbIndex index) throws SQLException {
        boolean tableExist = vecDbQueryExecutor.vectorTableExists(connection, embeddingTable.name());

        if (embeddingTable.createOption() == CreateOption.CREATE_NONE) {
            if (!tableExist) {
                throw new IllegalStateException(
                        "VecDB table does not exist: " + embeddingTable.name());
            }
            if (tableExist) prepareIndexForExistingTable(connection, embeddingTable, index);
        }

        if (embeddingTable.createOption() == CreateOption.CREATE_IF_NOT_EXISTS) {
            if (tableExist) prepareIndexForExistingTable(connection, embeddingTable, index);
            else {
                if (index != null && index.createOption() == CreateOption.CREATE_NONE) {
                    throw new IllegalArgumentException("CREATE_NONE is not supported for VecDB tables");
                }
                createTable(connection, embeddingTable, index);
            }
        }
        if (embeddingTable.createOption() == CreateOption.CREATE_OR_REPLACE) {
            if (tableExist) vecDbQueryExecutor.dropVectorTable(connection, embeddingTable.name());
            createTable(connection, embeddingTable, index);

        }

    }

    /**
     * Creates a VecDB table, passing its annotations and optional index configuration as JSON.
     *
     * @param connection active connection to the target Oracle Database
     * @param embeddingTable table to create
     * @param index index to create with the table, or {@code null} to use the VecDB default
     * @throws SQLException if {@code DBMS_VECTOR_DATABASE.CREATE_VECTOR_TABLE} fails
     */
    private void createTable(Connection connection, VecDbEmbeddingTable embeddingTable, VecDbIndex index) throws SQLException {
        String indexJson = index == null ? null : VecDbIndexJsonMapper.toJson(index);
        String tableAnnotations = VecDbEmbeddingTableJsonMapper.annotationsToJson(embeddingTable.annotations());
        vecDbQueryExecutor.createVectorTable(connection, embeddingTable, tableAnnotations, indexJson);
    }

    /**
     * Applies the configured index lifecycle to an existing VecDB table.
     *
     * @param connection active connection to the target Oracle Database
     * @param embeddingTable existing VecDB table
     * @param index index configuration, or {@code null} to leave the current index unchanged
     * @throws SQLException if an index description, creation, or rebuild operation fails
     */
    private void prepareIndexForExistingTable(Connection connection, VecDbEmbeddingTable embeddingTable, VecDbIndex index) throws SQLException {
        if(index == null) return;
        String tableName = embeddingTable.name();
        boolean indexExist = vecDbQueryExecutor.indexExists(connection, tableName);
        switch (index.createOption()) {
            case CREATE_NONE:
                break;
            case CREATE_IF_NOT_EXISTS:
                if (!indexExist) {
                    vecDbQueryExecutor.createIndex(connection, tableName, VecDbIndexJsonMapper.toJson(index));
                }
                break;
            case CREATE_OR_REPLACE:
                if (!indexExist) {
                    vecDbQueryExecutor.createIndex(connection, tableName, VecDbIndexJsonMapper.toJson(index));
                }
                else vecDbQueryExecutor.rebuildIndex(connection, tableName, VecDbIndexJsonMapper.toJson(index));
                break;
        }
    }
}
