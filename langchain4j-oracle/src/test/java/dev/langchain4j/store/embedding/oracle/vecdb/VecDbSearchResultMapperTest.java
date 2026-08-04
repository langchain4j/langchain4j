package dev.langchain4j.store.embedding.oracle.vecdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.oracle.vecdb.enums.VecDbDistanceMetric;
import dev.langchain4j.store.embedding.oracle.vecdb.mapper.VecDbSearchResultMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class VecDbSearchResultMapperTest {

    @Test
    void testMapsCosineDistanceToScore() {
        EmbeddingMatch<TextSegment> match = mapSingleResult(0.5, VecDbDistanceMetric.COSINE);

        assertThat(match.score()).isCloseTo(0.75, within(1e-12));
    }

    @Test
    void testMapsEuclideanDistanceToScore() {
        EmbeddingMatch<TextSegment> match = mapSingleResult(3.0, VecDbDistanceMetric.EUCLIDEAN);

        assertThat(match.score()).isCloseTo(0.25, within(1e-12));
    }

    @Test
    void testMapsSquaredEuclideanDistanceToScore() {
        EmbeddingMatch<TextSegment> euclideanSquared = mapSingleResult(9.0, VecDbDistanceMetric.EUCLIDEAN_SQUARED);
        EmbeddingMatch<TextSegment> l2Squared = mapSingleResult(9.0, VecDbDistanceMetric.L2_SQUARED);

        assertThat(euclideanSquared.score()).isCloseTo(0.25, within(1e-12));
        assertThat(l2Squared.score()).isCloseTo(0.25, within(1e-12));
    }

    @Test
    void testMapsManhattanDistanceToScore() {
        EmbeddingMatch<TextSegment> match = mapSingleResult(3.0, VecDbDistanceMetric.MANHATTAN);

        assertThat(match.score()).isCloseTo(0.25, within(1e-12));
    }

    @Test
    void testMapsDotDistanceToScore() {
        EmbeddingMatch<TextSegment> match = mapSingleResult(-0.75, VecDbDistanceMetric.DOT);

        assertThat(match.score()).isCloseTo(0.75, within(1e-12));
    }

    @Test
    void testClampsDotScoreToLangChainRange() {
        String responseJson = """
                {
                  "results": [
                    {"id": "upper", "distance": -2.0},
                    {"id": "lower", "distance": 1.0}
                  ]
                }
                """;

        List<EmbeddingMatch<TextSegment>> matches = VecDbSearchResultMapper.map(
                        responseJson, 0.0, VecDbDistanceMetric.DOT)
                .matches();

        assertThat(matches).extracting(EmbeddingMatch::score).containsExactly(1.0, 0.0);
    }

    @Test
    void testFiltersMatchesBelowMinimumScore() {
        String responseJson = """
                {
                  "results": [
                    {"id": "included", "distance": 0.2},
                    {"id": "excluded", "distance": 1.0}
                  ]
                }
                """;

        EmbeddingSearchResult<TextSegment> result =
                VecDbSearchResultMapper.map(responseJson, 0.75, VecDbDistanceMetric.COSINE);

        assertThat(result.matches()).extracting(EmbeddingMatch::embeddingId).containsExactly("included");
    }

    @Test
    void testPreservesVecDbResultOrder() {
        String responseJson = """
                {
                  "results": [
                    {"id": "second-id", "distance": 0.1},
                    {"id": "first-id", "distance": 0.2},
                    {"id": "third-id", "distance": 0.3}
                  ]
                }
                """;

        EmbeddingSearchResult<TextSegment> result =
                VecDbSearchResultMapper.map(responseJson, 0.0, VecDbDistanceMetric.COSINE);

        assertThat(result.matches())
                .extracting(EmbeddingMatch::embeddingId)
                .containsExactly("second-id", "first-id", "third-id");
    }

    @Test
    void testReconstructsEmbeddingAndTextSegment() {
        String responseJson = """
                {
                  "results": [
                    {
                      "id": "vector-id",
                      "distance": 0.0,
                      "vector": [0.1, -0.2, 0.3],
                      "metadata": {
                        "text": "Oracle VecDB",
                        "tenant": "langchain4j",
                        "priority": 2
                      }
                    }
                  ]
                }
                """;

        EmbeddingMatch<TextSegment> match = VecDbSearchResultMapper.map(responseJson, 0.0, VecDbDistanceMetric.COSINE)
                .matches()
                .get(0);

        assertThat(match.embeddingId()).isEqualTo("vector-id");
        assertThat(match.embedding().vector()).containsExactly(0.1f, -0.2f, 0.3f);
        assertThat(match.embedded().text()).isEqualTo("Oracle VecDB");
        assertThat(match.embedded().metadata().toMap())
                .containsEntry("tenant", "langchain4j")
                .containsEntry("priority", 2)
                .doesNotContainKey("text");
    }

    @Test
    void testReturnsNullTextSegmentForVectorOnlyRecord() {
        String responseJson = """
                {
                  "results": [
                    {
                      "id": "vector-only",
                      "distance": 0.0,
                      "vector": [1.0, 0.0]
                    }
                  ]
                }
                """;

        EmbeddingMatch<TextSegment> match = VecDbSearchResultMapper.map(responseJson, 0.0, VecDbDistanceMetric.COSINE)
                .matches()
                .get(0);

        assertThat(match.embedding()).isNotNull();
        assertThat(match.embedded()).isNull();
    }

    @Test
    void testRejectsMissingIdOrDistance() {
        String missingId = """
                {"results": [{"distance": 0.0}]}
                """;
        String missingDistance = """
                {"results": [{"id": "vector-id"}]}
                """;

        assertThatThrownBy(() -> VecDbSearchResultMapper.map(missingId, 0.0, VecDbDistanceMetric.COSINE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing required property \"id\"");
        assertThatThrownBy(() -> VecDbSearchResultMapper.map(missingDistance, 0.0, VecDbDistanceMetric.COSINE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing required property \"distance\"");
    }

    @Test
    void testRejectsInvalidVectorValues() {
        String nonNumericVector = """
                {
                  "results": [
                    {"id": "vector-id", "distance": 0.0, "vector": [1.0, "invalid"]}
                  ]
                }
                """;
        String valueOutsideFloat32Range = """
                {
                  "results": [
                    {"id": "vector-id", "distance": 0.0, "vector": [3.5e38]}
                  ]
                }
                """;

        assertThatThrownBy(() -> VecDbSearchResultMapper.map(nonNumericVector, 0.0, VecDbDistanceMetric.COSINE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("\"vector\" entries must be finite numbers");
        assertThatThrownBy(() -> VecDbSearchResultMapper.map(valueOutsideFloat32Range, 0.0, VecDbDistanceMetric.COSINE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("\"vector\" entry is outside the FLOAT32 range");
    }

    @Test
    void testRejectsDistanceOutsideMetricRange() {
        assertThatThrownBy(() -> mapSingleResult(-0.1, VecDbDistanceMetric.COSINE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cosine \"distance\" must be between 0 and 2");
        assertThatThrownBy(() -> mapSingleResult(2.1, VecDbDistanceMetric.COSINE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cosine \"distance\" must be between 0 and 2");
        assertThatThrownBy(() -> mapSingleResult(-0.1, VecDbDistanceMetric.EUCLIDEAN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EUCLIDEAN \"distance\" must not be negative");
    }

    private static EmbeddingMatch<TextSegment> mapSingleResult(double distance, VecDbDistanceMetric distanceMetric) {
        String responseJson = """
                {
                  "results": [
                    {"id": "vector-id", "distance": %s}
                  ]
                }
                """.formatted(distance);

        return VecDbSearchResultMapper.map(responseJson, 0.0, distanceMetric)
                .matches()
                .get(0);
    }
}
