package dev.langchain4j.store.cassio;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BatchStatement;
import com.datastax.oss.driver.api.core.cql.BatchStatementBuilder;
import com.datastax.oss.driver.api.core.cql.BatchType;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Table representing persistence for LangChain operations
 */
@Slf4j
public class ClusteredTable extends AbstractCassandraTable<ClusteredRecord> {

    /**
     * Prepared statements
     */
    private PreparedStatement findPartitionStatement;
    private PreparedStatement deletePartitionStatement;
    private PreparedStatement deleteRowStatement;
    private PreparedStatement insertRowStatement;
    private PreparedStatement findRowStatement;

    /**
     * Constructor with the mandatory parameters.
     *
     * @param session
     *      cassandra Session
     * @param keyspaceName
     *      keyspace name
     * @param tableName
     *      table name
     */
    public ClusteredTable(@NonNull CqlSession session, @NonNull String  keyspaceName, @NonNull  String tableName) {
        super(session, keyspaceName, tableName);
    }

    /**
     * Prepare statements on first request.
     */
    private synchronized void prepareStatements() {
        if (findPartitionStatement == null) {
            findPartitionStatement = cqlSession.prepare(
                    "select * from " + keyspaceName + "." + tableName
                            + " where " + PARTITION_ID + " = ? ");
            deletePartitionStatement = cqlSession.prepare(
                    "delete from " + keyspaceName + "." + tableName
                            + " where " + PARTITION_ID + " = ? ");
            findRowStatement = cqlSession.prepare(
                    "select * from " + keyspaceName + "." + tableName
                            + " where " + PARTITION_ID + " = ? "
                            + " and " + ROW_ID + " = ? ");
            deleteRowStatement = cqlSession.prepare(
                    "delete from " + keyspaceName + "." + tableName
                            + " where " + PARTITION_ID + " = ? "
                            + " and " + ROW_ID + " = ? ");
            insertRowStatement = cqlSession.prepare(
                    "insert into " + keyspaceName + "." + tableName
                            + " (" + PARTITION_ID + ", " + ROW_ID + ", " + BODY_BLOB + ") "
                            + " values (?, ?, ?)");
        }
    }

    /** {@inheritDoc} */
    @Override
    public void create() {
        cqlSession.execute("CREATE TABLE IF NOT EXISTS " + keyspaceName + "." + tableName + " ("
                        + PARTITION_ID + " text, "
                        + ROW_ID + " timeuuid, "
                        + BODY_BLOB + " text, "
                        + "PRIMARY KEY ((" + PARTITION_ID + "), " + ROW_ID + ")) "
                        + "WITH CLUSTERING ORDER BY (" + ROW_ID + " DESC)");
        log.info("+ Table '{}' has been created (if needed).", tableName);
    }

    /** {@inheritDoc} */
    @Override
    public void put(@NonNull ClusteredRecord row) {
        prepareStatements();
        cqlSession.execute(insertRowStatement.bind(row.getPartitionId(), row.getRowId(), row.getBody()));
    }

    /** {@inheritDoc} */
    @Override
    public ClusteredRecord mapRow(@NonNull Row row) {
        return new ClusteredRecord(
                row.getString(PARTITION_ID),
                row.getUuid(ROW_ID),
                row.getString(BODY_BLOB));
    }

    /**
     * Find a partition.
     *
     * @param partitionDd
     *      partition id
     * @return
     *      list of rows
     */
    public List<ClusteredRecord> findPartition(@NonNull String partitionDd) {
        prepareStatements();
        return cqlSession.execute(findPartitionStatement.bind(partitionDd))
                .all().stream()
                .map(this::mapRow)
                .collect(Collectors.toList());
    }

    /**
     * Update the history in one go.
     *
     * @param rows
     *      current rows.
     */
    public void upsertPartition(List<ClusteredRecord> rows) {
        prepareStatements();
        if (rows != null && !rows.isEmpty()) {
            BatchStatementBuilder batch = BatchStatement.builder(BatchType.LOGGED);
            String currentPartitionId = null;
            for (ClusteredRecord row : rows) {
                if (currentPartitionId != null && !currentPartitionId.equals(row.getPartitionId())) {
                    log.warn("Not all rows are part of the same partition");
                }
                currentPartitionId = row.getPartitionId();
                batch.addStatement(insertRowStatement.bind(row.getPartitionId(), row.getRowId(), row.getBody()));
            }
            cqlSession.execute(batch.build());
        }
    }

    /**
     * Find a row by its id.
     * @param partition
     *      partition id
     * @param rowId
     *      row id
     * @return
     *      record if exists
     */
    public Optional<ClusteredRecord> findById(String partition, UUID rowId) {
        prepareStatements();
        return Optional.ofNullable(cqlSession
                        .execute(findRowStatement.bind(partition, rowId))
                        .one()).map(this::mapRow);
    }

    /**
     * Delete Partition.
     *
     * @param partitionId
     *     delete the whole partition
     */
    public void deletePartition(@NonNull String partitionId) {
        prepareStatements();
        cqlSession.execute(deletePartitionStatement.bind(partitionId));
    }

    /**
     * Delete one row.
     *
     * @param partitionId
     *      current session
     * @param rowId
     *      message id
     */
    public void delete(@NonNull String partitionId, @NonNull UUID rowId) {
        prepareStatements();
        cqlSession.execute(deleteRowStatement.bind(partitionId, rowId));
    }

    /**
     * Insert Row.
     *
     * @param partitionId
     *      partition id
     * @param rowId
     *      rowId
     * @param bodyBlob
     *      body
     */
    public void insert(@NonNull String partitionId, @NonNull UUID rowId, @NonNull String bodyBlob) {
        prepareStatements();
        cqlSession.execute(insertRowStatement.bind(partitionId,rowId, bodyBlob));
    }

}
