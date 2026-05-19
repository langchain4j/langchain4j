package dev.langchain4j.store.embedding.milvus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.milvus.param.MetricType;
import org.junit.jupiter.api.Test;

class MapperTest {

    private static final double TOLERANCE = 1e-9;

    @Test
    void cosineSimilarity_is_mapped_to_zero_to_one_range() {
        // Cosine similarity is in [-1, 1] where 1 is most similar.
        // RelevanceScore.fromCosineSimilarity maps that to [0, 1] via (score + 1) / 2.
        assertThat(Mapper.toRelevanceScore(MetricType.COSINE, 1.0)).isCloseTo(1.0, within(TOLERANCE));
        assertThat(Mapper.toRelevanceScore(MetricType.COSINE, 0.0)).isCloseTo(0.5, within(TOLERANCE));
        assertThat(Mapper.toRelevanceScore(MetricType.COSINE, -1.0)).isCloseTo(0.0, within(TOLERANCE));
    }

    /**
     * Regression test for <a href="https://github.com/langchain4j/langchain4j/issues/5251">#5251</a>.
     *
     * <p>Before the fix, Mapper unconditionally applied {@code fromCosineSimilarity} to the raw
     * Milvus score for every metric type. For L2, the raw score is a Euclidean distance (lower is
     * more similar, range {@code [0, +∞)}), so feeding it to a cosine mapping inverted the
     * semantic meaning of "relevance" — a perfect match (distance 0) scored {@code 0.5}, and a
     * distant point (distance 1) scored {@code 1.0}. Closer is now monotonically higher.
     */
    @Test
    void l2_distance_is_mapped_so_that_closer_is_higher_relevance() {
        double exactMatch = Mapper.toRelevanceScore(MetricType.L2, 0.0);
        double nearMatch = Mapper.toRelevanceScore(MetricType.L2, 0.1);
        double farMatch = Mapper.toRelevanceScore(MetricType.L2, 10.0);

        // Identical vectors (distance 0) should yield the maximum relevance score of 1.
        assertThat(exactMatch).isCloseTo(1.0, within(TOLERANCE));

        // Relevance must decrease monotonically as distance grows.
        assertThat(nearMatch).isLessThan(exactMatch);
        assertThat(farMatch).isLessThan(nearMatch);

        // The output stays within the (0, 1] contract documented on Mapper.toRelevanceScore.
        assertThat(farMatch).isGreaterThan(0.0).isLessThanOrEqualTo(1.0);
    }

    @Test
    void inner_product_uses_cosine_mapping_for_normalized_vectors() {
        // For L2-normalized vectors, IP == cosine similarity, so the mapping should match COSINE.
        assertThat(Mapper.toRelevanceScore(MetricType.IP, 1.0))
                .isCloseTo(Mapper.toRelevanceScore(MetricType.COSINE, 1.0), within(TOLERANCE));
        assertThat(Mapper.toRelevanceScore(MetricType.IP, 0.0))
                .isCloseTo(Mapper.toRelevanceScore(MetricType.COSINE, 0.0), within(TOLERANCE));
    }

    @Test
    void inner_product_clamps_unnormalized_scores_to_unit_interval() {
        // Unnormalized IP scores can exceed [-1, 1]; the relevance is clamped to [0, 1] so the
        // EmbeddingMatch contract holds even when callers operate on unnormalized vectors.
        assertThat(Mapper.toRelevanceScore(MetricType.IP, 5.0)).isCloseTo(1.0, within(TOLERANCE));
        assertThat(Mapper.toRelevanceScore(MetricType.IP, -5.0)).isCloseTo(0.0, within(TOLERANCE));
    }
}
