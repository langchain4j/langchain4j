package dev.langchain4j.store.embedding.oracle.vecdb.mapper;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.oracle.vecdb.enums.VecDbApiVersion;
import dev.langchain4j.store.embedding.oracle.vecdb.enums.VecDbDistanceMetric;
import org.junit.jupiter.api.Test;

/** Verifies translation of LangChain4j search requests into VecDB search parameters. */
class VecDbSearchRequestMapperTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** Verifies mapping of a dense query vector and LangChain4j maximum result count. */
    @Test
    void testMapsDenseQueryAndMaximumResults() throws JsonProcessingException {
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(new Embedding(new float[] {0.1f, -0.2f, 0.3f}))
                .maxResults(7)
                .minScore(0.8)
                .build();

        VecDbSearchRequestMapper.VecDbSearchParameters parameters =
                VecDbSearchRequestMapper.map(request, null, VecDbApiVersion.V23_26_3);

        assertJsonEquals(parameters.queryJson(), """
                {
                  "vector": [0.1, -0.2, 0.3]
                }
                """);
        assertThat(parameters.maxResults()).isEqualTo(7);
        assertThat(parameters.includeVectors()).isTrue();
    }

    /** Verifies that a request without metadata filtering passes a null filter document. */
    @Test
    void testReturnsNullFiltersWhenRequestHasNoFilter() {
        EmbeddingSearchRequest request = request();

        VecDbSearchRequestMapper.VecDbSearchParameters parameters =
                VecDbSearchRequestMapper.map(request, null, VecDbApiVersion.V23_26_3);

        assertThat(parameters.filtersJson()).isNull();
    }

    /** Verifies that supported LangChain4j metadata filters are translated into VecDB QBE JSON. */
    @Test
    void testMapsSupportedMetadataFilter() throws JsonProcessingException {
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(new Embedding(new float[] {0.1f, 0.2f}))
                .filter(metadataKey("tenant").isEqualTo("acme"))
                .maxResults(5)
                .build();

        VecDbSearchRequestMapper.VecDbSearchParameters parameters =
                VecDbSearchRequestMapper.map(request, null, VecDbApiVersion.V23_26_3);

        assertJsonEquals(parameters.filtersJson(), """
                {
                  "tenant": {
                    "$eq": "acme"
                  }
                }
                """);
    }

    /** Verifies that an explicit store search metric is placed in search advanced options. */
    @Test
    void testMapsExplicitSearchDistanceMetric() throws JsonProcessingException {
        VecDbSearchRequestMapper.VecDbSearchParameters parameters =
                VecDbSearchRequestMapper.map(request(), VecDbDistanceMetric.EUCLIDEAN, VecDbApiVersion.V23_26_3);

        assertJsonEquals(parameters.advancedOptionsJson(), """
                {
                  "distance_metric": "EUCLIDEAN"
                }
                """);
    }

    /** Verifies that omitted search metric configuration produces no advanced-options document. */
    @Test
    void testOmitsAdvancedOptionsWhenSearchDistanceMetricIsNotConfigured() {
        VecDbSearchRequestMapper.VecDbSearchParameters parameters =
                VecDbSearchRequestMapper.map(request(), null, VecDbApiVersion.V23_26_3);

        assertThat(parameters.advancedOptionsJson()).isNull();
    }

    /** Verifies that a null LangChain4j search request is rejected before JSON mapping. */
    @Test
    void testRejectsNullRequest() {
        assertThatThrownBy(
                        () -> VecDbSearchRequestMapper.map(null, VecDbDistanceMetric.COSINE, VecDbApiVersion.V23_26_3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("request cannot be null");
    }

    private static EmbeddingSearchRequest request() {
        return EmbeddingSearchRequest.builder()
                .queryEmbedding(new Embedding(new float[] {0.1f, 0.2f}))
                .maxResults(3)
                .build();
    }

    private static void assertJsonEquals(String actual, String expected) throws JsonProcessingException {
        assertThat(readJson(actual)).isEqualTo(readJson(expected));
    }

    private static JsonNode readJson(String json) throws JsonProcessingException {
        return OBJECT_MAPPER.readTree(json);
    }
}
