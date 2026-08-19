package dev.langchain4j.store.embedding.oracle.vecdb.mapper;

import static dev.langchain4j.store.embedding.oracle.CreateOption.CREATE_IF_NOT_EXISTS;
import static dev.langchain4j.store.embedding.oracle.CreateOption.CREATE_NONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.store.embedding.oracle.vecdb.VecDbDistributeParameters;
import dev.langchain4j.store.embedding.oracle.vecdb.VecDbMetadataIndex;
import dev.langchain4j.store.embedding.oracle.vecdb.VecDbVectorIndex;
import dev.langchain4j.store.embedding.oracle.vecdb.enums.VecDbDistanceMetric;
import dev.langchain4j.store.embedding.oracle.vecdb.enums.VecDbQuantizationAlgorithm;
import dev.langchain4j.store.embedding.oracle.vecdb.enums.VecDbQuantizationType;
import org.junit.jupiter.api.Test;

/** Verifies vector-index, metadata-index, and parallel-creation JSON for the newer API dialect. */
class VecDbIndexJsonMapperTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** Verifies that absent vector, metadata, and parallel configuration produces no index JSON. */
    @Test
    void testReturnsNullWhenNoIndexConfigurationIsProvided() {
        assertThat(VecDbIndexJsonMapper.toJson(null, null, null)).isNull();
    }

    /** Verifies that vector-index {@code CREATE_NONE} disables automatic vector indexing. */
    @Test
    void testMapsVectorIndexCreateNoneToDisabledAutoIndex() throws JsonProcessingException {
        VecDbVectorIndex vectorIndex =
                VecDbVectorIndex.ivfIndexBuilder().createOption(CREATE_NONE).build();

        assertJsonEquals(VecDbIndexJsonMapper.toJson(vectorIndex, null, null), """
                {
                  "vector_index_params": {
                    "auto_index": false,
                    "organization": "PARTITIONS"
                  }
                }
                """);
    }

    /** Verifies that metadata-index {@code CREATE_NONE} disables automatic metadata indexing. */
    @Test
    void testMapsMetadataIndexCreateNoneToDisabledAutoIndex() throws JsonProcessingException {
        VecDbMetadataIndex metadataIndex = VecDbMetadataIndex.builder()
                .createOption(CREATE_NONE)
                .includePath("tenant")
                .excludePath("text")
                .build();

        assertJsonEquals(VecDbIndexJsonMapper.toJson(null, metadataIndex, null), """
                {
                  "metadata_index_params": {
                    "auto_index": false
                  }
                }
                """);
    }

    /** Verifies mapping of IVF configuration to the newer nested vector-index JSON. */
    @Test
    void testMapsIvfIndexToNewerApiJson() throws JsonProcessingException {
        VecDbVectorIndex vectorIndex = VecDbVectorIndex.ivfIndexBuilder()
                .createOption(CREATE_IF_NOT_EXISTS)
                .distanceMetric(VecDbDistanceMetric.EUCLIDEAN)
                .accuracy(90)
                .quantizationType(VecDbQuantizationType.SCALAR)
                .compressionRatio(4)
                .onlineBuild(true)
                .partitions(100)
                .build();

        assertJsonEquals(VecDbIndexJsonMapper.toJson(vectorIndex, null, null), """
                {
                  "vector_index_params": {
                    "auto_index": true,
                    "organization": "PARTITIONS",
                    "distance_metric": "EUCLIDEAN",
                    "accuracy": 90,
                    "quantization_type": "SCALAR",
                    "compression_ratio": 4,
                    "online_build": true,
                    "advanced_params": {
                      "partitions": 100
                    }
                  }
                }
                """);
    }

    /** Verifies mapping of HNSW, quantization, and distribution options to newer JSON. */
    @Test
    void testMapsHnswIndexToNewerApiJson() throws JsonProcessingException {
        VecDbDistributeParameters distributeParameters = VecDbDistributeParameters.builder()
                .distributeMethod("PARTITION")
                .serviceName("vector_service")
                .build();
        VecDbVectorIndex vectorIndex = VecDbVectorIndex.hnswIndexBuilder()
                .createOption(CREATE_IF_NOT_EXISTS)
                .distanceMetric(VecDbDistanceMetric.DOT)
                .accuracy(95)
                .quantizationType(VecDbQuantizationType.SCALAR)
                .compressionRatio(8)
                .onlineBuild(false)
                .distributeParameters(distributeParameters)
                .neighbors(32)
                .efConstruction(128)
                .rescoreFactor(4)
                .quantizationAlgorithm(VecDbQuantizationAlgorithm.UNIFORM_QUANTIZATION)
                .build();

        assertJsonEquals(VecDbIndexJsonMapper.toJson(vectorIndex, null, null), """
                {
                  "vector_index_params": {
                    "auto_index": true,
                    "organization": "INMEMORY GRAPH",
                    "distance_metric": "DOT",
                    "accuracy": 95,
                    "quantization_type": "SCALAR",
                    "compression_ratio": 8,
                    "online_build": false,
                    "distribute_params": {
                      "distribute_method": "PARTITION",
                      "service_name": "vector_service"
                    },
                    "advanced_params": {
                      "neighbors": 32,
                      "efConstruction": 128,
                      "rescore_factor": 4,
                      "algorithm": "uniform_quantization"
                    }
                  }
                }
                """);
    }

    /** Verifies mapping of metadata include/exclude paths and automatic path discovery. */
    @Test
    void testMapsMetadataPathsAndAutomaticIndexing() throws JsonProcessingException {
        VecDbMetadataIndex metadataIndex = VecDbMetadataIndex.builder()
                .createOption(CREATE_IF_NOT_EXISTS)
                .autoIndex(false)
                .includePath("tenant")
                .includePath("category")
                .excludePath("text")
                .build();

        assertJsonEquals(VecDbIndexJsonMapper.toJson(null, metadataIndex, null), """
                {
                  "metadata_index_params": {
                    "auto_index": false,
                    "include_paths": ["tenant", "category"],
                    "exclude_paths": ["text"]
                  }
                }
                """);
    }

    /** Verifies composition of vector index, metadata index, and parallel creation in one document. */
    @Test
    void testCombinesVectorMetadataAndParallelCreation() throws JsonProcessingException {
        VecDbVectorIndex vectorIndex = VecDbVectorIndex.ivfIndexBuilder()
                .createOption(CREATE_IF_NOT_EXISTS)
                .build();
        VecDbMetadataIndex metadataIndex =
                VecDbMetadataIndex.builder().createOption(CREATE_IF_NOT_EXISTS).build();

        assertJsonEquals(VecDbIndexJsonMapper.toJson(vectorIndex, metadataIndex, 4), """
                {
                  "vector_index_params": {
                    "auto_index": true,
                    "organization": "PARTITIONS",
                    "advanced_params": {
                      "partitions": 5
                    }
                  },
                  "metadata_index_params": {
                    "auto_index": true
                  },
                  "parallel_creation": 4
                }
                """);
    }

    /** Verifies omission of an unspecified metric so Oracle can apply documented selection rules. */
    @Test
    void testOmitsDistanceMetricWhenItIsNotConfigured() throws JsonProcessingException {
        VecDbVectorIndex vectorIndex = VecDbVectorIndex.ivfIndexBuilder()
                .createOption(CREATE_IF_NOT_EXISTS)
                .build();

        JsonNode indexParameters = readJson(VecDbIndexJsonMapper.toJson(vectorIndex, null, null));

        assertThat(indexParameters.path("vector_index_params").has("distance_metric"))
                .isFalse();
    }

    /** Verifies that an IVF index remains executable when custom JSON omits its partition count. */
    @Test
    void testUsesDefaultIvfPartitionsWhenTheyAreNotConfigured() throws JsonProcessingException {
        VecDbVectorIndex vectorIndex = VecDbVectorIndex.ivfIndexBuilder()
                .createOption(CREATE_IF_NOT_EXISTS)
                .distanceMetric(VecDbDistanceMetric.EUCLIDEAN)
                .build();

        JsonNode vectorParameters = readJson(VecDbIndexJsonMapper.toJson(vectorIndex, null, null))
                .path("vector_index_params");

        assertThat(vectorParameters.path("advanced_params").path("partitions").intValue())
                .isEqualTo(5);
    }

    /** Verifies the selector document used when dropping only metadata indexes. */
    @Test
    void testMapsMetadataIndexDropSelector() throws JsonProcessingException {
        assertJsonEquals(VecDbIndexJsonMapper.dropMetadataIndexesJson(), """
                {
                  "index_type": "metadata"
                }
                """);
    }

    /** Verifies that parallel creation must use a strictly positive worker count. */
    @Test
    void testRejectsNonPositiveParallelCreation() {
        assertThatThrownBy(() -> VecDbIndexJsonMapper.toJson(null, null, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("parallelCreation must be greater than zero, but is: 0");
    }

    private static void assertJsonEquals(String actual, String expected) throws JsonProcessingException {
        assertThat(readJson(actual)).isEqualTo(readJson(expected));
    }

    private static JsonNode readJson(String json) throws JsonProcessingException {
        return OBJECT_MAPPER.readTree(json);
    }
}
