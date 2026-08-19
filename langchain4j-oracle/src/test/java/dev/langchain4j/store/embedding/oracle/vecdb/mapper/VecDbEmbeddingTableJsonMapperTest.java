package dev.langchain4j.store.embedding.oracle.vecdb.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.store.embedding.oracle.vecdb.OracleVecDbEmbeddingStore;
import dev.langchain4j.store.embedding.oracle.vecdb.enums.VecDbApiVersion;
import dev.langchain4j.store.embedding.oracle.vecdb.enums.VecDbDistanceMetric;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Verifies table JSON, description mapping, index discovery, and effective metric resolution. */
class VecDbEmbeddingTableJsonMapperTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** Verifies serialization of the newer API table-creation parameters. */
    @Test
    void testMapsTableParameters() throws JsonProcessingException {
        assertJsonEquals(VecDbEmbeddingTableJsonMapper.tableParametersToJson(), """
                {
                  "auto_generate_id": false
                }
                """);
    }

    /** Verifies that absent table annotations are represented by a null JSON parameter. */
    @Test
    void testReturnsNullForEmptyAnnotations() {
        assertThat(VecDbEmbeddingTableJsonMapper.annotationsToJson(Map.of())).isNull();
    }

    /** Verifies serialization of user-supplied table annotations. */
    @Test
    void testMapsAnnotations() throws JsonProcessingException {
        Map<String, Object> annotations = new LinkedHashMap<>();
        annotations.put("application", "knowledge-search");
        annotations.put("revision", 2);
        annotations.put("production", true);

        assertJsonEquals(VecDbEmbeddingTableJsonMapper.annotationsToJson(annotations), """
                {
                  "application": "knowledge-search",
                  "revision": 2,
                  "production": true
                }
                """);
    }

    /** Verifies case-insensitive mapping of the earlier API table-description response. */
    @Test
    void testMapsEarlierTableDescriptionCaseInsensitively() {
        String response = """
                {
                  "TABLE_NAME": "EARLIER_VECTORS",
                  "DESCRIPTION": "Earlier table",
                  "ANNOTATIONS": {
                    "application": "chatbot"
                  }
                }
                """;

        OracleVecDbEmbeddingStore.VectorTableDescription description =
                VecDbEmbeddingTableJsonMapper.descriptionFromJson(response);

        assertThat(description.tableName()).isEqualTo("EARLIER_VECTORS");
        assertThat(description.comment()).isEqualTo("Earlier table");
        assertThat(description.annotations()).containsExactly(entry("application", "chatbot"));
    }

    /** Verifies mapping of the newer API table-description response. */
    @Test
    void testMapsNewerTableDescription() {
        String response = """
                {
                  "table_name": "NEWER_VECTORS",
                  "comment": "Newer table",
                  "annotations": {
                    "application": "search",
                    "revision": 3
                  }
                }
                """;

        OracleVecDbEmbeddingStore.VectorTableDescription description =
                VecDbEmbeddingTableJsonMapper.descriptionFromJson(response);

        assertThat(description.tableName()).isEqualTo("NEWER_VECTORS");
        assertThat(description.comment()).isEqualTo("Newer table");
        assertThat(description.annotations())
                .containsExactlyInAnyOrderEntriesOf(Map.of("application", "search", "revision", 3));
    }

    /** Verifies that missing annotations become an empty immutable public map. */
    @Test
    void testReturnsEmptyImmutableAnnotationsWhenTheyAreMissing() {
        String response = """
                {
                  "table_name": "VECTORS",
                  "comment": null
                }
                """;

        OracleVecDbEmbeddingStore.VectorTableDescription description =
                VecDbEmbeddingTableJsonMapper.descriptionFromJson(response);

        assertThat(description.comment()).isNull();
        assertThat(description.annotations()).isEmpty();
        assertThatThrownBy(() -> description.annotations().put("application", "search"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /** Verifies that the earlier response's dense-index name identifies a physical vector index. */
    @Test
    void testFindsEarlierVectorIndexFromDenseIndexName() {
        String response = """
                {
                  "TABLE_NAME": "VECTORS",
                  "DENSE_IDX_NAME": "VECIDX_VECTORS_1",
                  "INDEX_PARAMS": {
                    "indexing": "auto"
                  }
                }
                """;

        VecDbEmbeddingTableJsonMapper.IndexStatus status =
                VecDbEmbeddingTableJsonMapper.indexStatusFromJson(response, VecDbApiVersion.V23_26_1);

        assertThat(status.vectorIndexExists()).isTrue();
        assertThat(status.metadataIndexExists()).isFalse();
    }

    /** Verifies that configured index parameters alone do not prove that an earlier index exists. */
    @Test
    void testDoesNotTreatEarlierIndexParametersAsPhysicalIndex() {
        String response = """
                {
                  "TABLE_NAME": "VECTORS",
                  "DENSE_IDX_NAME": null,
                  "INDEX_PARAMS": {
                    "indexing": "auto",
                    "distance_metric": "COSINE"
                  }
                }
                """;

        VecDbEmbeddingTableJsonMapper.IndexStatus status =
                VecDbEmbeddingTableJsonMapper.indexStatusFromJson(response, VecDbApiVersion.V23_26_1);

        assertThat(status.vectorIndexExists()).isFalse();
        assertThat(status.metadataIndexExists()).isFalse();
    }

    /** Verifies discovery of vector and metadata indexes from newer explicit description fields. */
    @Test
    void testFindsNewerVectorAndMetadataIndexesFromExplicitEntries() {
        String response = """
                {
                  "table_name": "VECTORS",
                  "indexes": [
                    {"index_name": "VECIDX_VECTORS_1", "index_type": "VECTOR"},
                    {"index_name": "MVI_VECTORS_1", "index_type": "METADATA"}
                  ]
                }
                """;

        VecDbEmbeddingTableJsonMapper.IndexStatus status =
                VecDbEmbeddingTableJsonMapper.indexStatusFromJson(response, VecDbApiVersion.V23_26_3);

        assertThat(status.vectorIndexExists()).isTrue();
        assertThat(status.metadataIndexExists()).isTrue();
    }

    /** Verifies index discovery from newer index-parameter fields when explicit entries are absent. */
    @Test
    void testFindsNewerIndexesFromIndexParametersFallback() {
        String response = """
                {
                  "table_name": "VECTORS",
                  "index_params": {
                    "vector_index_params": {
                      "auto_index": true
                    },
                    "metadata_index_params": {
                      "auto_index": false,
                      "include_paths": ["tenant"]
                    }
                  }
                }
                """;

        VecDbEmbeddingTableJsonMapper.IndexStatus status =
                VecDbEmbeddingTableJsonMapper.indexStatusFromJson(response, VecDbApiVersion.V23_26_3);

        assertThat(status.vectorIndexExists()).isTrue();
        assertThat(status.metadataIndexExists()).isTrue();
    }

    /** Verifies discovery of the flat distance metric used by the earlier description format. */
    @Test
    void testResolvesEarlierFlatDistanceMetric() {
        String response = """
                {
                  "TABLE_NAME": "VECTORS",
                  "DENSE_IDX_NAME": "VECIDX_VECTORS_1",
                  "INDEX_PARAMS": {
                    "distance_metric": "euclidean"
                  }
                }
                """;

        VecDbDistanceMetric metric =
                VecDbEmbeddingTableJsonMapper.effectiveDistanceMetricFromJson(response, VecDbApiVersion.V23_26_1);

        assertThat(metric).isEqualTo(VecDbDistanceMetric.EUCLIDEAN);
    }

    /** Verifies discovery of the nested vector-index metric used by the newer description format. */
    @Test
    void testResolvesNewerNestedDistanceMetric() {
        String response = """
                {
                  "table_name": "VECTORS",
                  "indexes": [
                    {"index_name": "VECIDX_VECTORS_1", "index_type": "VECTOR"}
                  ],
                  "index_params": {
                    "vector_index_params": {
                      "distance_metric": "MANHATTAN"
                    }
                  }
                }
                """;

        VecDbDistanceMetric metric =
                VecDbEmbeddingTableJsonMapper.effectiveDistanceMetricFromJson(response, VecDbApiVersion.V23_26_3);

        assertThat(metric).isEqualTo(VecDbDistanceMetric.MANHATTAN);
    }

    /** Verifies Oracle's cosine default when no physical vector index exists. */
    @Test
    void testUsesCosineWhenNoVectorIndexExists() {
        String response = """
                {
                  "table_name": "VECTORS",
                  "index_params": {
                    "vector_index_params": {
                      "distance_metric": "DOT"
                    }
                  }
                }
                """;

        VecDbDistanceMetric metric =
                VecDbEmbeddingTableJsonMapper.effectiveDistanceMetricFromJson(response, VecDbApiVersion.V23_26_3);

        assertThat(metric).isEqualTo(VecDbDistanceMetric.COSINE);
    }

    /** Verifies Oracle's cosine default when an index description omits its metric. */
    @Test
    void testUsesCosineWhenVectorIndexHasNoDistanceMetric() {
        String response = """
                {
                  "table_name": "VECTORS",
                  "indexes": [
                    {"index_name": "VECIDX_VECTORS_1", "index_type": "VECTOR"}
                  ],
                  "index_params": {
                    "vector_index_params": {
                      "auto_index": true
                    }
                  }
                }
                """;

        VecDbDistanceMetric metric =
                VecDbEmbeddingTableJsonMapper.effectiveDistanceMetricFromJson(response, VecDbApiVersion.V23_26_3);

        assertThat(metric).isEqualTo(VecDbDistanceMetric.COSINE);
    }

    /** Verifies rejection of a database-discovered metric the store cannot convert to a score. */
    @Test
    void testRejectsUnsupportedDiscoveredDistanceMetric() {
        String response = """
                {
                  "TABLE_NAME": "VECTORS",
                  "DENSE_IDX_NAME": "VECIDX_VECTORS_1",
                  "INDEX_PARAMS": {
                    "distance_metric": "HAMMING"
                  }
                }
                """;

        assertThatThrownBy(() -> VecDbEmbeddingTableJsonMapper.effectiveDistanceMetricFromJson(
                        response, VecDbApiVersion.V23_26_1))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("HAMMING")
                .hasMessageContaining("LangChain4j scores");
    }

    /** Verifies that malformed table-description JSON is rejected. */
    @Test
    void testRejectsMalformedDescriptionResponse() {
        assertThatThrownBy(() -> VecDbEmbeddingTableJsonMapper.descriptionFromJson("not-json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("response is not valid JSON");
    }

    /** Verifies that a description without a table name cannot become a public description. */
    @Test
    void testRejectsDescriptionWithoutTableName() {
        assertThatThrownBy(() -> VecDbEmbeddingTableJsonMapper.descriptionFromJson("{\"comment\":\"missing name\"}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("table_name")
                .hasMessageContaining("missing or blank");
    }

    private static Map.Entry<String, Object> entry(String key, Object value) {
        return Map.entry(key, value);
    }

    private static void assertJsonEquals(String actual, String expected) throws JsonProcessingException {
        assertThat(readJson(actual)).isEqualTo(readJson(expected));
    }

    private static JsonNode readJson(String json) throws JsonProcessingException {
        return OBJECT_MAPPER.readTree(json);
    }
}
