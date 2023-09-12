package dev.langchain4j.store.embedding.redis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import redis.clients.jedis.search.Schema;

import java.util.HashMap;
import java.util.Map;

/**
 * Redis Schema Description
 */
@Builder
@AllArgsConstructor
public class RedisSchema {

    public static final String SCORE_FIELD_NAME = "vector_score";
    private static final Schema.VectorField.VectorAlgo DEFAULT_VECTOR_ALGORITHM = Schema.VectorField.VectorAlgo.HNSW;
    private static final MetricType DEFAULT_METRIC_TYPE = MetricType.COSINE;
    private static final DataType DEFAULT_DATA_TYPE = DataType.FLOAT32;

    /* Redis schema field settings */

    @Builder.Default
    private String indexName = "embedding-index";
    @Builder.Default
    private String prefix = "embedding:";
    @Builder.Default
    private String vectorFieldName = "vector";
    @Builder.Default
    private String scalarFieldName = "text";
    @Builder.Default
    private String metadataFieldName = "metadata";

    /* Vector field settings */

    @Builder.Default
    private Schema.VectorField.VectorAlgo vectorAlgorithm = DEFAULT_VECTOR_ALGORITHM;
    private int dimension;
    @Builder.Default
    private MetricType metricType = DEFAULT_METRIC_TYPE;
    /**
     * only support FLOAT32
     *
     * @see dev.langchain4j.data.embedding.Embedding
     */
    @Builder.Default
    private DataType dataType = DataType.FLOAT32;

    public RedisSchema(int dimension) {
        this.dimension = dimension;
    }

    public Schema toSchema() {
        Map<String, Object> vectorAttrs = new HashMap<>();
        vectorAttrs.put("DIM", dimension);
        vectorAttrs.put("DISTANCE_METRIC", metricType.getName());
        vectorAttrs.put("TYPE", dataType.getName());
        vectorAttrs.put("INITIAL_CAP", 5);
        return new Schema()
                .addTextField(scalarFieldName, 1.0)
                .addTextField("metadata", 1.0)
                .addVectorField(vectorFieldName, vectorAlgorithm, vectorAttrs);
    }

    public String getIndexName() {
        return indexName;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getVectorFieldName() {
        return vectorFieldName;
    }

    public String getScalarFieldName() {
        return scalarFieldName;
    }

    public String getMetadataFieldName() {
        return metadataFieldName;
    }
}
