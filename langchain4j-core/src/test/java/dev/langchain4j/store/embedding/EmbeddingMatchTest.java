package dev.langchain4j.store.embedding;

import dev.langchain4j.data.embedding.Embedding;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

class EmbeddingMatchTest implements WithAssertions {
    @Test
    void test() {
        EmbeddingMatch<String> em =
                new EmbeddingMatch<>(0.5, "embeddingId", Embedding.from(new float[] {3.5f, -2f}), "abc");

        assertThat(em.score()).isEqualTo(0.5);
        assertThat(em.embeddingId()).isEqualTo("embeddingId");
        assertThat(em.embedding().vector()).contains(3.5f, -2f);
        assertThat(em.embedded()).isEqualTo("abc");

        assertThat(em)
                .hasToString(
                        "EmbeddingMatch { score = 0.5, embedded = abc, embeddingId = embeddingId, embedding = Embedding { vector = [3.5, -2.0] } }");
    }

    @Test
    void equals_hash() {
        EmbeddingMatch<String> em1 =
                new EmbeddingMatch<>(0.5, "embeddingId", Embedding.from(new float[] {3.5f, -2f}), "abc");
        EmbeddingMatch<String> em2 =
                new EmbeddingMatch<>(0.5, "embeddingId", Embedding.from(new float[] {3.5f, -2f}), "abc");

        assertThat(em1)
                .isEqualTo(em1)
                .isNotEqualTo(null)
                .isNotEqualTo(new Object())
                .isEqualTo(em2)
                .hasSameHashCodeAs(em2);

        assertThat(new EmbeddingMatch<>(0.2, "embeddingId", Embedding.from(new float[] {3.5f, -2f}), "abc"))
                .isNotEqualTo(em1);
        assertThat(new EmbeddingMatch<>(0.5, "changed", Embedding.from(new float[] {3.5f, -2f}), "abc"))
                .isNotEqualTo(em1);
        assertThat(new EmbeddingMatch<>(0.5, "embeddingId", Embedding.from(new float[] {8.5f, -2f}), "abc"))
                .isNotEqualTo(em1);
        assertThat(new EmbeddingMatch<>(0.5, "embeddingId", Embedding.from(new float[] {3.5f, -2f}), "xyz"))
                .isNotEqualTo(em1);
    }
}
