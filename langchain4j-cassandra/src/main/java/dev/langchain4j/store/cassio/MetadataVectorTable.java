package dev.langchain4j.store.cassio;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.core.data.CqlVector;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Table representing persistence for Vector Stores support. As the name stated
 * it holds both a vector and a metadata map.
 * <code>
 * CREATE TABLE langchain4j.test_embedding_store (
 *     row_id text PRIMARY KEY,
 *     attributes_blob text,
 *     body_blob text,
 *     metadata_s map&lt;text, text&gt;,
 *     vector vector&lt;float, 11&gt;
 * );
 * </code>
 */
@Slf4j
@Getter
public class MetadataVectorTable extends AbstractCassandraTable<MetadataVectorRecord> {

    /**
     * Dimension of the vector in use
     */
    private final int vectorDimension;

    /**
     * Similarity Metric, Vector is indexed with this metric.
     */
    private final SimilarityMetric similarityMetric;

    /**
     * Constructor with mandatory parameters.
     *
     * @param session         cassandra session
     * @param keyspaceName    keyspace name
     * @param tableName       table name
     * @param vectorDimension vector dimension
     */
    public MetadataVectorTable(CqlSession session, String keyspaceName, String tableName, int vectorDimension) {
        this(session, keyspaceName, tableName, vectorDimension, SimilarityMetric.COSINE);
    }

    /**
     * Constructor with mandatory parameters.
     *
     * @param session         cassandra session
     * @param keyspaceName    keyspace name
     * @param tableName       table name
     * @param vectorDimension vector dimension
     * @param metric          similarity metric
     */
    public MetadataVectorTable(CqlSession session, String keyspaceName, String tableName, int vectorDimension, SimilarityMetric metric) {
        super(session, keyspaceName, tableName);
        this.vectorDimension = vectorDimension;
        this.similarityMetric = metric;
        create();
    }

    /**
     * Create table and indexes if not exist.
     */
    public void create() {
        // Create Table
        String cqlQueryCreateTable = "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                ROW_ID + " text, " +
                ATTRIBUTES_BLOB + " text, " +
                BODY_BLOB + " text, " +
                METADATA_S + " map<text, text>, " +
                VECTOR + " vector<float, " + vectorDimension + ">, " +
                "PRIMARY KEY (" +
                ROW_ID + ")" +
                ")";
        cqlSession.execute(cqlQueryCreateTable);
        log.info("Table '{}' has been created (if needed).", tableName);
        cqlSession.execute(
                "CREATE CUSTOM INDEX IF NOT EXISTS idx_vector_" + tableName
                        + " ON " + tableName + " (" + VECTOR + ") "
                        + "USING 'org.apache.cassandra.index.sai.StorageAttachedIndex' "
                        + "WITH OPTIONS = { 'similarity_function': '" +  similarityMetric.getOption() + "'};");
        log.info("Index '{}' has been created (if needed).", "idx_vector_" + tableName);
        // Create Metadata Index
        cqlSession.execute(
                "CREATE CUSTOM INDEX IF NOT EXISTS eidx_metadata_s_" + tableName
                        + " ON " + tableName + " (ENTRIES(" + METADATA_S + ")) "
                        + "USING 'org.apache.cassandra.index.sai.StorageAttachedIndex';");
        log.info("Index '{}' has been created (if needed).", "eidx_metadata_s_" + tableName);
    }

    /** {@inheritDoc} */
    public void put(MetadataVectorRecord row) {
        cqlSession.execute(row.insertStatement(keyspaceName, tableName));
    }

    /**
     * Marshall a row as a result.
     *
     * @param cqlRow cql row
     * @return resul
     */
    private AnnResult<MetadataVectorRecord> mapResult(Row cqlRow) {
        if (cqlRow == null) return null;
        AnnResult<MetadataVectorRecord> res = new AnnResult<>();
        res.setEmbedded(mapRow(cqlRow));
        res.setSimilarity(cqlRow.getFloat(COLUMN_SIMILARITY));
        log.debug("Result similarity '{}' for embedded id='{}'", res.getSimilarity(), res.getEmbedded().getRowId());
        return res;
    }

    @Override
    @SuppressWarnings("unchecked")
    public MetadataVectorRecord mapRow(Row cqlRow) {
        if (cqlRow == null) return null;

        MetadataVectorRecord record = new MetadataVectorRecord();
        record.setRowId(cqlRow.getString(ROW_ID));
        record.setBody(cqlRow.getString(BODY_BLOB));
        record.setVector(((CqlVector<Float>) Objects.requireNonNull(cqlRow.getObject(VECTOR)))
                    .stream().collect(Collectors.toList()));
        if (cqlRow.getColumnDefinitions().contains(ATTRIBUTES_BLOB)) {
            record.setAttributes(cqlRow.getString(ATTRIBUTES_BLOB));
        }
        if (cqlRow.getColumnDefinitions().contains(METADATA_S)) {
            record.setMetadata(cqlRow.getMap(METADATA_S, String.class, String.class));
        }
        return record;
    }

    /**
     * Compute Similarity Search.
     *
     * @param query
     *    current query
     * @return
     *      results
     */
    public List<AnnResult<MetadataVectorRecord>> similaritySearch(AnnQuery query) {
        StringBuilder cqlQuery = new StringBuilder("SELECT " + ROW_ID + ","
                + VECTOR + "," + BODY_BLOB + ","
                + ATTRIBUTES_BLOB + "," + METADATA_S + ",");
        cqlQuery.append(query.getMetric().getFunction()).append("(vector, :vector) as ").append(COLUMN_SIMILARITY);
        cqlQuery.append(" FROM ").append(tableName);
        if (query.getMetaData() != null && !query.getMetaData().isEmpty()) {
            cqlQuery.append(" WHERE ");
            boolean first = true;
            for (Map.Entry<String, String> entry : query.getMetaData().entrySet()) {
                if (!first) {
                    cqlQuery.append(" AND ");
                }
                cqlQuery.append(METADATA_S).append("['")
                        .append(entry.getKey())
                        .append("'] = '")
                        .append(entry.getValue()).append("'");
                first = false;
            }
        }
        cqlQuery.append(" ORDER BY vector ANN OF :vector ");
        cqlQuery.append(" LIMIT :maxRecord");
        log.debug("Query on table '{}' with vector size '{}' and max record='{}'",
               tableName,
                "[" + query.getEmbeddings().size() + "]",
                "" + (query.getTopK() > 0 ? query.getTopK() : DEFAULT_RECORD_COUNT));
        return cqlSession.execute(SimpleStatement.builder(cqlQuery.toString())
                        .addNamedValue("vector", CqlVector.newInstance(query.getEmbeddings()))
                        .addNamedValue("maxRecord", query.getTopK() > 0 ? query.getTopK() : DEFAULT_RECORD_COUNT)
                        .build())
                .all().stream() // max record is small and pagination is not needed
                .map(this::mapResult)
                .filter(r -> r.getSimilarity() >= query.getThreshold())
                .collect(Collectors.toList());
    }


}
