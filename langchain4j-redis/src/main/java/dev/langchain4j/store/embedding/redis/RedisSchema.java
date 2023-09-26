package dev.langchain4j.store.embedding.redis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import redis.clients.jedis.search.schemafields.SchemaField;
import redis.clients.jedis.search.schemafields.TextField;
import redis.clients.jedis.search.schemafields.VectorField;
import redis.clients.jedis.search.schemafields.VectorField.VectorAlgorithm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.store.embedding.redis.MetricType.COSINE;
import static redis.clients.jedis.search.schemafields.VectorField.VectorAlgorithm.HNSW;

/**
 * Redis Schema Description
 */
@Builder
@AllArgsConstructor
class RedisSchema {

    public static final String SCORE_FIELD_NAME = "vector_score";
    private static final String JSON_PATH_PREFIX = "$.";
    private static final VectorAlgorithm DEFAULT_VECTOR_ALGORITHM = HNSW;
    private static final MetricType DEFAULT_METRIC_TYPE = COSINE;

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
    private List<String> metadataFieldsName = new ArrayList<>();

    /* Vector field settings */

    @Builder.Default
    private VectorAlgorithm vectorAlgorithm = DEFAULT_VECTOR_ALGORITHM;
    private int dimension;
    @Builder.Default
    private MetricType metricType = DEFAULT_METRIC_TYPE;

    public RedisSchema(int dimension) {
        this.dimension = dimension;
    }

    public SchemaField[] toSchemaFields() {
        Map<String, Object> vectorAttrs = new HashMap<>();
        vectorAttrs.put("DIM", dimension);
        vectorAttrs.put("DISTANCE_METRIC", metricType.name());
        vectorAttrs.put("TYPE", "FLOAT32");
        vectorAttrs.put("INITIAL_CAP", 5);
        List<SchemaField> fields = new ArrayList<>();
        fields.add(TextField.of(JSON_PATH_PREFIX + scalarFieldName).as(scalarFieldName).weight(1.0));
        fields.add(VectorField.builder()
                .fieldName(JSON_PATH_PREFIX + vectorFieldName)
                .algorithm(vectorAlgorithm)
                .attributes(vectorAttrs)
                .as(vectorFieldName)
                .build());

        if (metadataFieldsName != null && !metadataFieldsName.isEmpty()) {
            for (String metadataFieldName : metadataFieldsName) {
                fields.add(TextField.of(JSON_PATH_PREFIX + metadataFieldName).as(metadataFieldName).weight(1.0));
            }
        }
        return fields.toArray(new SchemaField[0]);
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

    public List<String> getMetadataFieldsName() {
        return metadataFieldsName;
    }
}
