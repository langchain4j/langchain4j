package dev.langchain4j.store.embedding.milvus;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.store.embedding.RelevanceScore;
import io.milvus.param.MetricType;
import org.junit.jupiter.api.Test;

class MapperTest {

    @Test
    void l2_distance_is_converted_to_relevance_score() {
        assertThat(Mapper.toRelevanceScore(0.0, MetricType.L2)).isEqualTo(1.0);
        assertThat(Mapper.toRelevanceScore(1.0, MetricType.L2)).isEqualTo(0.5);
        assertThat(Mapper.toRelevanceScore(3.0, MetricType.L2)).isEqualTo(0.25);
    }

    @Test
    void non_l2_score_uses_cosine_similarity_conversion() {
        assertThat(Mapper.toRelevanceScore(0.6, MetricType.COSINE)).isEqualTo(RelevanceScore.fromCosineSimilarity(0.6));
        assertThat(Mapper.toRelevanceScore(0.6, MetricType.IP)).isEqualTo(RelevanceScore.fromCosineSimilarity(0.6));
    }
}
