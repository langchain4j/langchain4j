package dev.langchain4j.store.embedding.oracle.vecdb;

import dev.langchain4j.store.embedding.oracle.CreateOption;
import dev.langchain4j.store.embedding.oracle.vecdb.enums.VecDbApiVersion;
import dev.langchain4j.store.embedding.oracle.vecdb.mapper.VecDbEmbeddingTableJsonMapper;
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
     * @param vectorIndex vector-index configuration, or {@code null} when none is configured
     * @param metadataIndex metadata-index configuration, or {@code null} when none is configured
     * @param parallelCreation index-creation parallelism, or {@code null} to use the database default
     * @return the VecDB API generation selected for the connected database
     * @throws SQLException if a {@code DBMS_VECTOR_DATABASE} operation fails
     * @throws dev.langchain4j.exception.UnsupportedFeatureException if the connected database is older than 23.26.1,
     *     or when the selected schema features are unavailable in its VecDB API generation
     * @throws IllegalStateException if the table is configured with {@link CreateOption#CREATE_NONE} but does not exist
     */
    VecDbApiVersion prepareSchema(
            Connection connection,
            VecDbEmbeddingTable embeddingTable,
            VecDbVectorIndex vectorIndex,
            VecDbMetadataIndex metadataIndex,
            Integer parallelCreation)
            throws SQLException {
        VecDbApiVersion apiVersion = VecDbSupport.requireSupported(connection);
        VecDbApiDialect.forVersion(apiVersion)
                .validateSchemaConfiguration(vectorIndex, metadataIndex, parallelCreation);
        boolean tableExist = vecDbQueryExecutor.vectorTableExists(connection, embeddingTable.name());

        if (embeddingTable.createOption() == CreateOption.CREATE_NONE) {
            if (!tableExist) {
                throw new IllegalStateException("VecDB table does not exist: " + embeddingTable.name());
            }
            if (tableExist) {
                prepareExistingTable(
                        connection, embeddingTable, vectorIndex, metadataIndex, parallelCreation, apiVersion);
            }
        }

        if (embeddingTable.createOption() == CreateOption.CREATE_IF_NOT_EXISTS) {
            if (tableExist) {
                prepareExistingTable(
                        connection, embeddingTable, vectorIndex, metadataIndex, parallelCreation, apiVersion);
            } else {
                createTable(connection, embeddingTable, vectorIndex, metadataIndex, parallelCreation, apiVersion);
            }
        }
        if (embeddingTable.createOption() == CreateOption.CREATE_OR_REPLACE) {
            if (tableExist) vecDbQueryExecutor.dropVectorTable(connection, embeddingTable.name(), apiVersion);
            createTable(connection, embeddingTable, vectorIndex, metadataIndex, parallelCreation, apiVersion);
        }
        return apiVersion;
    }

    private void prepareExistingTable(
            Connection connection,
            VecDbEmbeddingTable embeddingTable,
            VecDbVectorIndex vectorIndex,
            VecDbMetadataIndex metadataIndex,
            Integer parallelCreation,
            VecDbApiVersion apiVersion)
            throws SQLException {
        prepareExistingTableLayout(connection, embeddingTable.name(), apiVersion);
        prepareIndexesForExistingTable(
                connection, embeddingTable, vectorIndex, metadataIndex, parallelCreation, apiVersion);
    }

    private void prepareExistingTableLayout(Connection connection, String tableName, VecDbApiVersion apiVersion)
            throws SQLException {
        VecDbTableLayout initialLayout = vecDbQueryExecutor.inspectTableLayout(connection, tableName);
        VecDbTableMigration migration = VecDbTableMigration.determine(apiVersion, initialLayout);
        if (!migration.isRequired()) {
            return;
        }

        for (VecDbTableMigration.Action action : migration.actions()) {
            vecDbQueryExecutor.applyTableMigration(connection, tableName, action);
        }

        VecDbTableLayout resultingLayout = vecDbQueryExecutor.inspectTableLayout(connection, tableName);
        VecDbTableMigration remainingMigration = VecDbTableMigration.determine(apiVersion, resultingLayout);
        if (remainingMigration.isRequired()) {
            throw new IllegalStateException("VecDB table migration did not produce the required layout for " + tableName
                    + "; remaining actions " + remainingMigration.actions());
        }
    }

    /**
     * Creates a VecDB table, passing its annotations and optional index configuration as JSON.
     *
     * @param connection active connection to the target Oracle Database
     * @param embeddingTable table to create
     * @param vectorIndex vector-index configuration
     * @param metadataIndex metadata-index configuration
     * @param parallelCreation index-creation parallelism
     * @throws SQLException if {@code DBMS_VECTOR_DATABASE.CREATE_VECTOR_TABLE} fails
     */
    private void createTable(
            Connection connection,
            VecDbEmbeddingTable embeddingTable,
            VecDbVectorIndex vectorIndex,
            VecDbMetadataIndex metadataIndex,
            Integer parallelCreation,
            VecDbApiVersion apiVersion)
            throws SQLException {
        String indexJson = VecDbApiDialect.forVersion(apiVersion)
                .indexParametersToJson(vectorIndex, metadataIndex, parallelCreation);
        String tableAnnotations = VecDbEmbeddingTableJsonMapper.annotationsToJson(embeddingTable.annotations());
        String tableParameters = VecDbEmbeddingTableJsonMapper.tableParametersToJson();
        vecDbQueryExecutor.createVectorTable(
                connection, apiVersion, embeddingTable, tableAnnotations, tableParameters, indexJson);
    }

    /**
     * Applies vector- and metadata-index lifecycle options to an existing VecDB table.
     *
     * @param connection active connection to the target Oracle Database
     * @param embeddingTable existing VecDB table
     * @param vectorIndex vector-index configuration
     * @param metadataIndex metadata-index configuration
     * @param parallelCreation index-creation parallelism
     * @throws SQLException if an index description, creation, or rebuild operation fails
     */
    private void prepareIndexesForExistingTable(
            Connection connection,
            VecDbEmbeddingTable embeddingTable,
            VecDbVectorIndex vectorIndex,
            VecDbMetadataIndex metadataIndex,
            Integer parallelCreation,
            VecDbApiVersion apiVersion)
            throws SQLException {
        boolean manageVectorIndex = isManaged(vectorIndex == null ? null : vectorIndex.createOption());
        boolean manageMetadataIndex = isManaged(metadataIndex == null ? null : metadataIndex.createOption());
        if (!manageVectorIndex && !manageMetadataIndex) return;

        String tableName = embeddingTable.name();
        VecDbQueryExecutor.IndexStatus indexStatus = vecDbQueryExecutor.indexStatus(connection, tableName, apiVersion);
        if (manageVectorIndex) {
            prepareVectorIndexForExistingTable(
                    connection, tableName, vectorIndex, parallelCreation, indexStatus.vectorIndexExists(), apiVersion);
        }
        if (manageMetadataIndex) {
            prepareMetadataIndexForExistingTable(
                    connection,
                    tableName,
                    metadataIndex,
                    parallelCreation,
                    indexStatus.metadataIndexExists(),
                    apiVersion);
        }
    }

    private void prepareVectorIndexForExistingTable(
            Connection connection,
            String tableName,
            VecDbVectorIndex vectorIndex,
            Integer parallelCreation,
            boolean indexExists,
            VecDbApiVersion apiVersion)
            throws SQLException {
        String indexJson =
                VecDbApiDialect.forVersion(apiVersion).indexParametersToJson(vectorIndex, null, parallelCreation);
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
            VecDbMetadataIndex metadataIndex,
            Integer parallelCreation,
            boolean indexExists,
            VecDbApiVersion apiVersion)
            throws SQLException {
        VecDbApiDialect dialect = VecDbApiDialect.forVersion(apiVersion);
        String indexJson = dialect.indexParametersToJson(null, metadataIndex, parallelCreation);
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
                    vecDbQueryExecutor.dropIndex(connection, tableName, dialect.dropMetadataIndexesJson());
                }
                vecDbQueryExecutor.createIndex(connection, tableName, indexJson);
                break;
        }
    }

    private static boolean isManaged(CreateOption createOption) {
        return createOption != null && createOption != CreateOption.CREATE_NONE;
    }
}
