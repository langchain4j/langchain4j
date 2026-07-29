package dev.langchain4j.store.embedding.oracle.vecdb;

import dev.langchain4j.store.embedding.oracle.SQLFilter;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Executes calls to {@code DBMS_VECTOR_DATABASE}.
 *
 * <p>This boundary keeps PL/SQL invocation and Oracle JDBC types out of the embedding store. Request JSON is prepared
 * before it reaches this interface, while response JSON is returned unchanged for dedicated response mappers.
 */
interface VecDbQueryExecutor {

    /** Returns whether a vector table with the given name exists. */
    boolean vectorTableExists(Connection connection, String tableName) throws SQLException;

    /** Creates a bring-your-own-vector table and returns the VecDB response JSON. */
    String createVectorTable(
            Connection connection,
            VecDbEmbeddingTable table,
            String annotationsJson,
            String tableParametersJson,
            String indexParametersJson)
            throws SQLException;

    /** Describes a vector table and returns the VecDB response JSON. */
    String describeVectorTable(Connection connection, String tableName) throws SQLException;

    /** Drops a vector table and returns the VecDB response JSON. */
    String dropVectorTable(Connection connection, String tableName) throws SQLException;

    /** Returns the current vector- and metadata-index state of a vector table. */
    IndexStatus indexStatus(Connection connection, String tableName) throws SQLException;

    /** Creates vector indexes, metadata indexes, or both and returns the VecDB response JSON. */
    String createIndex(Connection connection, String tableName, String indexParametersJson) throws SQLException;

    /** Drops the indexes selected by {@code indexParametersJson} and returns the VecDB response JSON. */
    String dropIndex(Connection connection, String tableName, String indexParametersJson) throws SQLException;

    /** Rebuilds the indexes selected by {@code indexParametersJson} and returns the VecDB response JSON. */
    String rebuildIndex(Connection connection, String tableName, String indexParametersJson) throws SQLException;

    /** Upserts one or more vectors and returns the VecDB response JSON. */
    String upsertVectors(Connection connection, String tableName, String vectorsJson) throws SQLException;

    /** Lists vectors by ID or pagination and returns the VecDB response JSON. */
    String listVectors(Connection connection, String tableName, String idsJson, int limit, int offset)
            throws SQLException;

    /**
     * Searches for similar vectors and returns the VecDB response JSON.
     *
     * @param queryJson JSON containing exactly one of {@code vector}, {@code id}, or {@code text}
     * @param filtersJson optional metadata filter JSON
     * @param maxResults maximum number of nearest results
     * @param includeVectors whether returned matches should include their stored vector
     * @param advancedOptionsJson optional search-time options
     */
    String search(
            Connection connection,
            String tableName,
            String queryJson,
            String filtersJson,
            int maxResults,
            boolean includeVectors,
            String advancedOptionsJson)
            throws SQLException;

    /**
     * Deletes vectors by ID and returns the VecDB response JSON.
     *
     * <p>{@code DBMS_VECTOR_DATABASE.DELETE_VECTORS} commits its changes automatically.
     */
    String deleteVectors(Connection connection, String tableName, String idsJson) throws SQLException;

    /**
     * Deletes every vector whose metadata matches the supplied SQL filter and returns the affected row count.
     *
     * <p>This uses direct table DML because {@code DBMS_VECTOR_DATABASE.DELETE_VECTORS} accepts IDs only.
     */
    int deleteVectorsByFilter(Connection connection, String tableName, SQLFilter filter) throws SQLException;

    /** Deletes every row from the vector table and returns the affected row count. */
    int deleteAllVectors(Connection connection, String tableName) throws SQLException;

    /** Index state returned by {@link #indexStatus(Connection, String)}. */
    record IndexStatus(boolean vectorIndexExists, boolean metadataIndexExists) {}
}
