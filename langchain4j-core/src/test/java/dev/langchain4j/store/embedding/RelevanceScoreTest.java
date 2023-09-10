package dev.langchain4j.store.embedding;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RelevanceScoreTest {

    @Test
    void should_convert_cosine_similarity_into_relevance_score() {
        assertThat(RelevanceScore.fromCosineSimilarity(-1)).isEqualTo(0);
        assertThat(RelevanceScore.fromCosineSimilarity(0)).isEqualTo(0.5);
        assertThat(RelevanceScore.fromCosineSimilarity(1)).isEqualTo(1);
    }
}