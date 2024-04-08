package dev.langchain4j.store.cassio;

import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.core.data.CqlVector;
import com.datastax.oss.driver.api.core.uuid.Uuids;
import lombok.Data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.store.cassio.AbstractCassandraTable.ATTRIBUTES_BLOB;
import static dev.langchain4j.store.cassio.AbstractCassandraTable.BODY_BLOB;
import static dev.langchain4j.store.cassio.AbstractCassandraTable.METADATA_S;
import static dev.langchain4j.store.cassio.AbstractCassandraTable.ROW_ID;
import static dev.langchain4j.store.cassio.AbstractCassandraTable.VECTOR;

/**
 * Record for the table metadata + vector.
 */
@Data
public class MetadataVectorRecord implements Serializable {

    /**
     * Identifier of the row in Cassandra
     */
    private String rowId;

    /**
     * Store special attributes
     */
    private String attributes;

    /**
     * Body, contains the Text Fragment.
     */
    private String body;

    /**
     * Metadata (for metadata filtering)
     */
    private Map<String, String> metadata = new HashMap<>();

    /**
     * Embeddings
     */
    private List<Float> vector;

    /**
     * Default Constructor
     */
    public MetadataVectorRecord() {
        this(Uuids.timeBased().toString(), null);
    }

    /**
     * Create a record with a vector.
     *
     * @param vector current vector.
     */
    public MetadataVectorRecord(List<Float> vector) {
        this(Uuids.timeBased().toString(), vector);
    }

    /**
     * Create a record with a vector.
     * @param rowId  identifier for the row
     * @param vector current vector.
     */
    public MetadataVectorRecord(String rowId, List<Float> vector) {
        this.rowId  = rowId;
        this.vector = vector;
    }

    /**
     * Build insert statement dynamically.
     *
     * @param keyspaceName
     *      keyspace name
     * @param tableName
     *      table bane
     * @return
     *      statement
     */
    public SimpleStatement insertStatement(String keyspaceName, String tableName) {
        if (rowId == null) throw new IllegalStateException("Row Id cannot be null");
        if (vector == null) throw new IllegalStateException("Vector cannot be null");
        return SimpleStatement.newInstance("INSERT INTO " + keyspaceName + "." + tableName + " ("
                        + ROW_ID + "," + VECTOR + "," + ATTRIBUTES_BLOB + "," + BODY_BLOB + "," + METADATA_S + ") VALUES (?,?,?,?,?)",
                rowId, CqlVector.newInstance(vector), attributes, body, metadata);
    }

}