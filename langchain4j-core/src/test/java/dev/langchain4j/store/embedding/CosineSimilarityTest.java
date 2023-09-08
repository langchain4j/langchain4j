package dev.langchain4j.store.embedding;

import dev.langchain4j.data.embedding.Embedding;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;

class CosineSimilarityTest {

    @Test
    void should_calculate_cosine_similarity() {
        Embedding embeddingA = Embedding.from(new float[]{1, 1, 1});
        Embedding embeddingB = Embedding.from(new float[]{-1, -1, -1});

        assertThat(CosineSimilarity.between(embeddingA, embeddingA)).isCloseTo(1, withPercentage(1));
        assertThat(CosineSimilarity.between(embeddingA, embeddingB)).isCloseTo(-1, withPercentage(1));
    }

    @Test
    void should_convert_relevance_score_into_cosine_similarity() {
        assertThat(CosineSimilarity.fromRelevanceScore(0)).isEqualTo(-1);
        assertThat(CosineSimilarity.fromRelevanceScore(0.5)).isEqualTo(0);
        assertThat(CosineSimilarity.fromRelevanceScore(1)).isEqualTo(1);
    }
}