package dev.langchain4j.store.embedding.oracle.vecdb.mapper;

import static dev.langchain4j.store.embedding.oracle.CreateOption.CREATE_IF_NOT_EXISTS;
import static dev.langchain4j.store.embedding.oracle.CreateOption.CREATE_NONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.store.embedding.oracle.vecdb.VecDbDistributeParameters;
import dev.langchain4j.store.embedding.oracle.vecdb.VecDbMetadataIndex;
import dev.langchain4j.store.embedding.oracle.vecdb.VecDbVectorIndex;
import dev.langchain4j.store.embedding.oracle.vecdb.enums.VecDbDistanceMetric;
import dev.langchain4j.store.embedding.oracle.vecdb.enums.VecDbQuantizationType;
import org.junit.jupiter.api.Test;

/** Verifies vector-index mapping and capability rejection for the earlier VecDB API dialect. */
class VecDbIndexJsonMapperLegacyTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** Verifies that the earlier API receives no index JSON when no index is configured. */
    @Test
    void testReturnsNullWhenNoIndexIsConfigured() {
        assertThat(VecDbIndexJsonMapperLegacy.toJson(null, null, null)).isNull();
    }

    /** Verifies that indexes configured with {@code CREATE_NONE} are omitted from earlier JSON. */
    @Test
    void testReturnsNullWhenIndexesUseCreateNone() {
        VecDbVectorIndex vectorIndex =
                VecDbVectorIndex.ivfIndexBuilder().createOption(CREATE_NONE).build();
        VecDbMetadataIndex metadataIndex =
                VecDbMetadataIndex.builder().createOption(CREATE_NONE).build();

        assertThat(VecDbIndexJsonMapperLegacy.toJson(vectorIndex, metadataIndex, null))
                .isNull();
    }

    /** Verifies mapping of an IVF index to the flat earlier {@code index_params} format. */
    @Test
    void testMapsIvfIndexToEarlierApiJson() throws JsonProcessingException {
        VecDbVectorIndex vectorIndex = VecDbVectorIndex.ivfIndexBuilder()
                .createOption(CREATE_IF_NOT_EXISTS)
                .distanceMetric(VecDbDistanceMetric.EUCLIDEAN)
                .accuracy(90)
                .partitions(100)
                .build();

        String actual = VecDbIndexJsonMapperLegacy.toJson(vectorIndex, null, null);

        assertJsonEquals(actual, """
                {
                  "indexing": "auto",
                  "organization": "PARTITIONS",
                  "distance_metric": "EUCLIDEAN",
                  "accuracy": 90,
                  "advanced_params": {
                    "partitions": 100
                  }
                }
                """);
    }

    /** Verifies mapping of an HNSW index to the flat earlier {@code index_params} format. */
    @Test
    void testMapsHnswIndexToEarlierApiJson() throws JsonProcessingException {
        VecDbVectorIndex vectorIndex = VecDbVectorIndex.hnswIndexBuilder()
                .createOption(CREATE_IF_NOT_EXISTS)
                .distanceMetric(VecDbDistanceMetric.DOT)
                .neighbors(32)
                .efConstruction(128)
                .build();

        String actual = VecDbIndexJsonMapperLegacy.toJson(vectorIndex, null, null);

        assertJsonEquals(actual, """
                {
                  "indexing": "auto",
                  "organization": "INMEMORY GRAPH",
                  "distance_metric": "DOT",
                  "advanced_params": {
                    "neighbors": 32,
                    "efConstruction": 128
                  }
                }
                """);
    }

    /** Verifies that an unspecified index metric is omitted so Oracle can select its default. */
    @Test
    void testOmitsDistanceMetricWhenItIsNotConfigured() throws JsonProcessingException {
        VecDbVectorIndex vectorIndex = VecDbVectorIndex.ivfIndexBuilder()
                .createOption(CREATE_IF_NOT_EXISTS)
                .partitions(10)
                .build();

        JsonNode indexParameters = readJson(VecDbIndexJsonMapperLegacy.toJson(vectorIndex, null, null));

        assertThat(indexParameters.has("distance_metric")).isFalse();
    }

    /** Verifies that custom earlier IVF JSON retains the package's default partition count. */
    @Test
    void testUsesDefaultIvfPartitionsWhenTheyAreNotConfigured() throws JsonProcessingException {
        VecDbVectorIndex vectorIndex = VecDbVectorIndex.ivfIndexBuilder()
                .createOption(CREATE_IF_NOT_EXISTS)
                .distanceMetric(VecDbDistanceMetric.EUCLIDEAN)
                .build();

        JsonNode indexParameters = readJson(VecDbIndexJsonMapperLegacy.toJson(vectorIndex, null, null));

        assertThat(indexParameters.path("advanced_params").path("partitions").intValue())
                .isEqualTo(5);
    }

    /** Verifies that metadata indexes are rejected because the earlier API cannot create them. */
    @Test
    void testRejectsManagedMetadataIndex() {
        VecDbMetadataIndex metadataIndex =
                VecDbMetadataIndex.builder().createOption(CREATE_IF_NOT_EXISTS).build();

        assertThatThrownBy(() -> VecDbIndexJsonMapperLegacy.toJson(null, metadataIndex, null))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessage("VecDB metadata indexes require Oracle Database 23.26.3 or later");
    }

    /** Verifies that parallel index creation is rejected for the earlier API. */
    @Test
    void testRejectsParallelIndexCreation() {
        assertThatThrownBy(() -> VecDbIndexJsonMapperLegacy.toJson(null, null, 2))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessage("VecDB parallel index creation require Oracle Database 23.26.3 or later");
    }

    /** Verifies that newer vector quantization options cannot leak into earlier index JSON. */
    @Test
    void testRejectsQuantization() {
        VecDbVectorIndex vectorIndex = VecDbVectorIndex.ivfIndexBuilder()
                .createOption(CREATE_IF_NOT_EXISTS)
                .quantizationType(VecDbQuantizationType.SCALAR)
                .compressionRatio(4)
                .build();

        assertThatThrownBy(() -> VecDbIndexJsonMapperLegacy.toJson(vectorIndex, null, null))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessage("VecDB quantizationType require Oracle Database 23.26.3 or later");
    }

    /** Verifies that online index builds are rejected when using the earlier API. */
    @Test
    void testRejectsOnlineBuild() {
        VecDbVectorIndex vectorIndex = VecDbVectorIndex.ivfIndexBuilder()
                .createOption(CREATE_IF_NOT_EXISTS)
                .onlineBuild(true)
                .build();

        assertThatThrownBy(() -> VecDbIndexJsonMapperLegacy.toJson(vectorIndex, null, null))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessage("VecDB onlineBuild require Oracle Database 23.26.3 or later");
    }

    /** Verifies that distributed HNSW parameters are rejected for the earlier API. */
    @Test
    void testRejectsDistributedHnswParameters() {
        VecDbDistributeParameters distributeParameters = VecDbDistributeParameters.builder()
                .serviceName("vector_service")
                .build();
        VecDbVectorIndex vectorIndex = VecDbVectorIndex.hnswIndexBuilder()
                .createOption(CREATE_IF_NOT_EXISTS)
                .distributeParameters(distributeParameters)
                .build();

        assertThatThrownBy(() -> VecDbIndexJsonMapperLegacy.toJson(vectorIndex, null, null))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessage("VecDB distributeParameters require Oracle Database 23.26.3 or later");
    }

    /** Verifies that HNSW rescore-factor configuration is rejected for the earlier API. */
    @Test
    void testRejectsHnswRescoreFactor() {
        VecDbVectorIndex vectorIndex = VecDbVectorIndex.hnswIndexBuilder()
                .createOption(CREATE_IF_NOT_EXISTS)
                .rescoreFactor(4)
                .build();

        assertThatThrownBy(() -> VecDbIndexJsonMapperLegacy.toJson(vectorIndex, null, null))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessage("VecDB rescoreFactor require Oracle Database 23.26.3 or later");
    }

    private static void assertJsonEquals(String actual, String expected) throws JsonProcessingException {
        assertThat(readJson(actual)).isEqualTo(readJson(expected));
    }

    private static JsonNode readJson(String json) throws JsonProcessingException {
        return OBJECT_MAPPER.readTree(json);
    }
}
