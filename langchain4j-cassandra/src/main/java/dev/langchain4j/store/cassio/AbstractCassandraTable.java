package dev.langchain4j.store.cassio;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Row;

import java.util.concurrent.CompletableFuture;

/**
 * Abstract class for table management at Cassandra level.
 *
 * @param <RECORD>
 *     object in use with Cassandra
 */
public abstract class AbstractCassandraTable<RECORD> {

    /**
     * Class needed to create a SAI Index.
     */
    public static final String SAI_INDEX_CLASSNAME = "org.apache.cassandra.index.sai.StorageAttachedIndex";

    /**
     * Table column names.
     */
    public static final String PARTITION_ID     = "partition_id";

    /**
     * Table column names.
     */
    public static final String ROW_ID           = "row_id";

    /**
     * Table column names.
     */
    public static final String ATTRIBUTES_BLOB  = "attributes_blob";

    /**
     * Table column names.
     */
    public static final String BODY_BLOB        = "body_blob";

    /**
     * Table column names.
     */
    public static final String METADATA_S       = "metadata_s";

    /**
     * Table column names.
     */
    public static final String VECTOR           = "vector";

    /**
     * Table column names.
     */
    public static final String COLUMN_SIMILARITY = "similarity";

    /**
     * Default Number of item retrieved
     */
    public static final int DEFAULT_RECORD_COUNT = 4;

    /** Session to Cassandra. */
    protected final CqlSession cqlSession;

    /** Destination keyspace. */
    protected final String keyspaceName;

    /** Destination table. */
    protected final String tableName;

    /**
     * Default cosntructor.
     *
     * @param session
     *      cassandra session
     * @param keyspaceName
     *      keyspace
     * @param tableName
     *     table Name
     */
    public AbstractCassandraTable(CqlSession session, String keyspaceName, String tableName) {
        this.cqlSession   = session;
        this.keyspaceName = keyspaceName;
        this.tableName    = tableName;
    }

    /**
     * Create table if not exist.
     */
    public abstract void create();

    /**
     * Upsert a row of the table.
     *
     * @param row
     *      current row
     */
    public abstract void put(RECORD row);

    /**
     * Should be table to map from a Cassandra row to a record.
     *
     * @param row
     *      current cassandra row
     * @return
     *      current record
     */
    public abstract RECORD mapRow(Row row);

    /**
     * Insert a row asynchronously.
     *
     * @param inputRow
     *      current row
     * @return
     *      output
     */
    public CompletableFuture<Void> putAsync(final RECORD inputRow) {
        return CompletableFuture.runAsync(() -> put(inputRow));
    }

    /**
     * Delete the table.
     */
    public void delete() {
        cqlSession.execute("DROP TABLE IF EXISTS " + keyspaceName + "." + tableName);
    }

    /**
     * Empty a table
     */
    public void clear() {
        cqlSession.execute("TRUNCATE " + keyspaceName + "." + tableName);
    }

    /**
     * Gets cqlSession
     *
     * @return value of cqlSession
     */
    public CqlSession getCqlSession() {
        return cqlSession;
    }
}
