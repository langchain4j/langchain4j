package dev.langchain4j.store.cassio;

import com.datastax.oss.driver.api.core.CqlSession;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility to work with CassIO and Astra
 */
@Slf4j
public class CassIO {

    private static CqlSession cqlSession;

    /**
     * Default constructor.
     */
    public CassIO() {
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    /**
     * Shutdown hook to close Session.
     */
    private final Thread shutdownHook = new Thread() {
        public void run() {
            if (cqlSession != null) {
                cqlSession.close();
            }
        }
    };

    /**
     * Accessing the session.
     *
     * @return
     *    the cassandra session
     */
    public static CqlSession getCqlSession() {
        if (cqlSession == null) {
            throw new IllegalStateException("CqlSession not initialized, please use init() method");
        }
        return cqlSession;
    }

    /**
     * Initialization from db is and region.
     *
     * @param cqlSession
     *      cassandra connection
     * @return
     *    the cassandra session initialized
     */
    public static synchronized CqlSession init(CqlSession cqlSession) {
        if (cqlSession == null) {
            throw new IllegalStateException("CqlSession not initialized, please use init() method");
        }
        CassIO.cqlSession = cqlSession;
        return cqlSession;
    }

    /**
     * Create a new table to store vectors.
     *
     * @param tableName
     *      table name
     * @param vectorDimension
     *      vector dimension
     * @return
     *      table to store vector
     */
    public static MetadataVectorTable metadataVectorTable(String tableName, int vectorDimension) {
        if (tableName == null || tableName.isEmpty()) throw new IllegalArgumentException("Table name must be provided");
        if (vectorDimension < 1) throw new IllegalArgumentException("Vector dimension must be greater than 0");
        return new MetadataVectorTable(
                getCqlSession(),
                cqlSession.getKeyspace().orElseThrow(() ->
                        new IllegalArgumentException("CqlSession does not select any keyspace")).asInternal(),
                tableName, vectorDimension);
    }
    
}
