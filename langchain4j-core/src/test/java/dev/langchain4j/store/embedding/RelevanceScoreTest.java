package dev.langchain4j.store.embedding;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class RelevanceScoreTest {

    @Test
    void should_convert_cosine_similarity_into_relevance_score() {
        assertThat(RelevanceScore.fromCosineSimilarity(-1)).isEqualTo(0);
        assertThat(RelevanceScore.fromCosineSimilarity(0)).isEqualTo(0.5);
        assertThat(RelevanceScore.fromCosineSimilarity(1)).isEqualTo(1);
    }

    @Test
    void should_convert_l2_distance_into_relevance_score() {
        assertThat(RelevanceScore.fromL2Distance(0)).isEqualTo(1.0);
        assertThat(RelevanceScore.fromL2Distance(1)).isEqualTo(0.5);
        assertThat(RelevanceScore.fromL2Distance(999999)).isCloseTo(0, offset(0.001));
    }
}