package dev.langchain4j.store.embedding.oracle.vecdb;

import dev.langchain4j.store.embedding.oracle.CreateOption;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Prepares the VecDB table, vector index, and metadata indexes required by an embedding store.
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
 * <p>When a table is created, its root index parameters are passed to
 * {@code DBMS_VECTOR_DATABASE.CREATE_VECTOR_TABLE}. A {@code null} index allows VecDB to apply its index defaults,
 * while an index configured with {@link CreateOption#CREATE_NONE} sets its {@code auto_index} value to {@code false}.
 * For an existing table, vector- and metadata-index lifecycle options are applied independently.
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
     * Creates, validates, or replaces a VecDB table and prepares its indexes according to their configured
     * {@link CreateOption}s.
     *
     * <p>If the table already exists and is reused, each index option is applied independently:
     * <ul>
     *     <li>{@link CreateOption#CREATE_NONE} leaves the existing index unchanged.</li>
     *     <li>{@link CreateOption#CREATE_IF_NOT_EXISTS} creates an index only when one is absent.</li>
     *     <li>{@link CreateOption#CREATE_OR_REPLACE} replaces an existing index or creates one when absent.</li>
     * </ul>
     *
     * @param connection active connection to the target Oracle Database; ownership remains with the caller
     * @param embeddingTable VecDB table configuration
     * @param indexParameters root index configuration, or {@code null} to use VecDB defaults for a new table and
     *     leave an existing table's index unchanged
     * @throws SQLException if a {@code DBMS_VECTOR_DATABASE} operation fails
     * @throws dev.langchain4j.exception.UnsupportedFeatureException if the connected database is older than 23.26.3
     * @throws IllegalStateException if the table is configured with {@link CreateOption#CREATE_NONE} but does not exist
     */
    void prepareSchema(Connection connection, VecDbEmbeddingTable embeddingTable, VecDbIndexParameters indexParameters)
            throws SQLException {
        VecDbSupport.requireSupported(connection);
        boolean tableExist = vecDbQueryExecutor.vectorTableExists(connection, embeddingTable.name());

        if (embeddingTable.createOption() == CreateOption.CREATE_NONE) {
            if (!tableExist) {
                throw new IllegalStateException("VecDB table does not exist: " + embeddingTable.name());
            }
            if (tableExist) {
                prepareIndexesForExistingTable(connection, embeddingTable, indexParameters);
            }
        }

        if (embeddingTable.createOption() == CreateOption.CREATE_IF_NOT_EXISTS) {
            if (tableExist) {
                prepareIndexesForExistingTable(connection, embeddingTable, indexParameters);
            } else {
                createTable(connection, embeddingTable, indexParameters);
            }
        }
        if (embeddingTable.createOption() == CreateOption.CREATE_OR_REPLACE) {
            if (tableExist) vecDbQueryExecutor.dropVectorTable(connection, embeddingTable.name());
            createTable(connection, embeddingTable, indexParameters);
        }
    }

    /**
     * Creates a VecDB table, passing its annotations and optional index configuration as JSON.
     *
     * @param connection active connection to the target Oracle Database
     * @param embeddingTable table to create
     * @param indexParameters root index configuration, or {@code null} to use the VecDB defaults
     * @throws SQLException if {@code DBMS_VECTOR_DATABASE.CREATE_VECTOR_TABLE} fails
     */
    private void createTable(
            Connection connection, VecDbEmbeddingTable embeddingTable, VecDbIndexParameters indexParameters)
            throws SQLException {
        String indexJson = indexParameters == null ? null : VecDbIndexJsonMapper.toJson(indexParameters);
        String tableAnnotations = VecDbEmbeddingTableJsonMapper.annotationsToJson(embeddingTable.annotations());
        String tableParameters = VecDbEmbeddingTableJsonMapper.tableParametersToJson();
        vecDbQueryExecutor.createVectorTable(connection, embeddingTable, tableAnnotations, tableParameters, indexJson);
    }

    /**
     * Applies vector- and metadata-index lifecycle options to an existing VecDB table.
     *
     * @param connection active connection to the target Oracle Database
     * @param embeddingTable existing VecDB table
     * @param indexParameters root index configuration
     * @throws SQLException if an index description, creation, or rebuild operation fails
     */
    private void prepareIndexesForExistingTable(
            Connection connection, VecDbEmbeddingTable embeddingTable, VecDbIndexParameters indexParameters)
            throws SQLException {
        if (indexParameters == null) return;

        VecDbVectorIndex vectorIndex = indexParameters.vectorIndex();
        VecDbMetadataIndex metadataIndex = indexParameters.metadataIndex();
        boolean manageVectorIndex = isManaged(vectorIndex == null ? null : vectorIndex.createOption());
        boolean manageMetadataIndex = isManaged(metadataIndex == null ? null : metadataIndex.createOption());
        if (!manageVectorIndex && !manageMetadataIndex) return;

        String tableName = embeddingTable.name();
        VecDbQueryExecutor.IndexStatus indexStatus = vecDbQueryExecutor.indexStatus(connection, tableName);
        if (manageVectorIndex) {
            prepareVectorIndexForExistingTable(
                    connection, tableName, indexParameters, vectorIndex, indexStatus.vectorIndexExists());
        }
        if (manageMetadataIndex) {
            prepareMetadataIndexForExistingTable(
                    connection, tableName, indexParameters, metadataIndex, indexStatus.metadataIndexExists());
        }
    }

    private void prepareVectorIndexForExistingTable(
            Connection connection,
            String tableName,
            VecDbIndexParameters indexParameters,
            VecDbVectorIndex vectorIndex,
            boolean indexExists)
            throws SQLException {
        String indexJson = vectorIndexJson(indexParameters, vectorIndex);
        switch (vectorIndex.createOption()) {
            case CREATE_NONE:
                break;
            case CREATE_IF_NOT_EXISTS:
                if (!indexExists) {
                    vecDbQueryExecutor.createIndex(connection, tableName, indexJson);
                }
                break;
            case CREATE_OR_REPLACE:
                if (!indexExists) {
                    vecDbQueryExecutor.createIndex(connection, tableName, indexJson);
                } else vecDbQueryExecutor.rebuildIndex(connection, tableName, indexJson);
                break;
        }
    }

    private void prepareMetadataIndexForExistingTable(
            Connection connection,
            String tableName,
            VecDbIndexParameters indexParameters,
            VecDbMetadataIndex metadataIndex,
            boolean indexExists)
            throws SQLException {
        String indexJson = metadataIndexJson(indexParameters, metadataIndex);
        switch (metadataIndex.createOption()) {
            case CREATE_NONE:
                break;
            case CREATE_IF_NOT_EXISTS:
                if (!indexExists) {
                    vecDbQueryExecutor.createIndex(connection, tableName, indexJson);
                }
                break;
            case CREATE_OR_REPLACE:
                if (indexExists) {
                    vecDbQueryExecutor.dropIndex(connection, tableName, VecDbIndexJsonMapper.dropMetadataIndexesJson());
                }
                vecDbQueryExecutor.createIndex(connection, tableName, indexJson);
                break;
        }
    }

    private static String vectorIndexJson(VecDbIndexParameters indexParameters, VecDbVectorIndex vectorIndex) {
        VecDbIndexParameters.Builder builder = VecDbIndexParameters.builder().vectorIndex(vectorIndex);
        if (indexParameters.parallelCreation() != null) {
            builder.parallelCreation(indexParameters.parallelCreation());
        }
        return VecDbIndexJsonMapper.toJson(builder.build());
    }

    private static String metadataIndexJson(VecDbIndexParameters indexParameters, VecDbMetadataIndex metadataIndex) {
        VecDbIndexParameters.Builder builder = VecDbIndexParameters.builder().metadataIndex(metadataIndex);
        if (indexParameters.parallelCreation() != null) {
            builder.parallelCreation(indexParameters.parallelCreation());
        }
        return VecDbIndexJsonMapper.toJson(builder.build());
    }

    private static boolean isManaged(CreateOption createOption) {
        return createOption != null && createOption != CreateOption.CREATE_NONE;
    }
}
