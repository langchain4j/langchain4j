package dev.langchain4j.store.embedding.redis;

import redis.clients.jedis.search.schemafields.SchemaField;
import redis.clients.jedis.search.schemafields.TextField;
import redis.clients.jedis.search.schemafields.VectorField;
import redis.clients.jedis.search.schemafields.VectorField.VectorAlgorithm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.internal.ValidationUtils.ensureTrue;
import static dev.langchain4j.store.embedding.redis.MetricType.COSINE;
import static redis.clients.jedis.search.schemafields.VectorField.VectorAlgorithm.HNSW;

/**
 * Redis Schema Description
 */
class RedisSchema {

    public static final String SCORE_FIELD_NAME = "vector_score";
    public static final String JSON_PATH = "$";
    public static final String JSON_PATH_PREFIX = "$.";
    private static final VectorAlgorithm DEFAULT_VECTOR_ALGORITHM = HNSW;
    private static final MetricType DEFAULT_METRIC_TYPE = COSINE;

    /* Redis schema field settings */

    private final String indexName;
    private final String prefix;
    private final String vectorFieldName;
    private final String scalarFieldName;
    private final Map<String, SchemaField> metadataConfig;

    /* Vector field settings */

    private final VectorAlgorithm vectorAlgorithm;
    private final Integer dimension;
    private final MetricType metricType;

    RedisSchema(String indexName,
                String prefix,
                String vectorFieldName,
                String scalarFieldName,
                VectorAlgorithm vectorAlgorithm,
                Integer dimension,
                MetricType metricType,
                Map<String, SchemaField> metadataConfig) {
        ensureTrue(prefix.endsWith(":"), "Prefix should end with a ':'");

        this.indexName = indexName;
        this.prefix = prefix;
        this.vectorFieldName = vectorFieldName;
        this.scalarFieldName = scalarFieldName;
        this.vectorAlgorithm = vectorAlgorithm;
        this.dimension = dimension;
        this.metricType = metricType;
        this.metadataConfig = metadataConfig;
    }

    SchemaField[] toSchemaFields() {
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
        // Add Metadata fields
        fields.addAll(metadataConfig.values());

        return fields.toArray(new SchemaField[0]);
    }

    String indexName() {
        return indexName;
    }

    String prefix() {
        return prefix;
    }

    String vectorFieldName() {
        return vectorFieldName;
    }

    String scalarFieldName() {
        return scalarFieldName;
    }

    Map<String, SchemaField> schemaFieldMap() {
        return metadataConfig;
    }

    static Builder builder() {
        return new Builder();
    }

    static class Builder {

        private String indexName;
        private String prefix = "embedding:";
        private String vectorFieldName = "vector";
        private String scalarFieldName = "text";
        private Map<String, SchemaField> metadataConfig = new HashMap<>();

        /* Vector field settings */

        private VectorAlgorithm vectorAlgorithm = DEFAULT_VECTOR_ALGORITHM;
        private Integer dimension;
        private final MetricType metricType = DEFAULT_METRIC_TYPE;

        Builder indexName(String indexName) {
            this.indexName = indexName;
            return this;
        }

        Builder prefix(String prefix) {
            this.prefix = prefix;
            return this;
        }

        Builder vectorFieldName(String vectorFieldName) {
            this.vectorFieldName = vectorFieldName;
            return this;
        }

        Builder scalarFieldName(String scalarFieldName) {
            this.scalarFieldName = scalarFieldName;
            return this;
        }

        Builder vectorAlgorithm(VectorAlgorithm vectorAlgorithm) {
            this.vectorAlgorithm = vectorAlgorithm;
            return this;
        }

        Builder dimension(Integer dimension) {
            this.dimension = dimension;
            return this;
        }

        Builder metadataConfig(Map<String, SchemaField> metadataConfig) {
            this.metadataConfig = metadataConfig;
            return this;
        }

        RedisSchema build() {
            return new RedisSchema(indexName, prefix, vectorFieldName, scalarFieldName, vectorAlgorithm, dimension, metricType, metadataConfig);
        }
    }
}
