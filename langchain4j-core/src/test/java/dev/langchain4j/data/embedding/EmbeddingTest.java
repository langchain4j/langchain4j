package dev.langchain4j.data.embedding;

import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.data.Percentage.withPercentage;

class EmbeddingTest implements WithAssertions {
    @Test
    public void test_equals_hash() {
        Embedding e1 = new Embedding(new float[]{1.0f, 2.0f, 3.0f});
        Embedding e2 = new Embedding(new float[]{1.0f, 2.0f, 3.0f});

        assertThat(e1)
                .isEqualTo(e1)
                .isNotEqualTo(null)
                .isNotEqualTo(new Object())
                .isEqualTo(e2)
                .hasSameHashCodeAs(e2);

        assertThat(new Embedding(new float[]{99.0f, 2.0f, 3.0f}))
                .isNotEqualTo(e1);
        assertThat(new Embedding(new float[]{1.0f, 2.0f, 3.0f, 4.0f}))
                .isNotEqualTo(e1);
    }

    @Test
    public void test_accessors() {
        Embedding e1 = new Embedding(new float[]{1.0f, 2.0f, 3.0f});
        assertThat(e1.dimension()).isEqualTo(3);
        assertThat(e1.magnitude()).isCloseTo(3.74f, withPercentage(0.1));
        assertThat(e1.vector()).containsExactly(1.0f, 2.0f, 3.0f);
        assertThat(e1.vectorAsList()).containsExactly(1.0f, 2.0f, 3.0f);

        assertThat(e1).hasToString("Embedding { vector = [1.0, 2.0, 3.0] }");
    }

    @Test
    public void test_cosineSimilarity() {
        Embedding e1 = new Embedding(new float[]{1.0f, 1.0f});
        Embedding e2 = new Embedding(new float[]{-1.0f, -1.0f});
        assertThat(e1.cosineSimilarity(e2)).isEqualTo(-1);

        Embedding e3 = new Embedding(new float[]{1.0f, 1.0f});
        Embedding e4 = new Embedding(new float[]{1.0f, -1.0f});
        assertThat(e3.cosineSimilarity(e4)).isEqualTo(0);

        Embedding e5 = new Embedding(new float[]{1.0f, 1.0f});
        Embedding e6 = new Embedding(new float[]{1.0f, 1.0f});
        assertThat(e5.cosineSimilarity(e6)).isEqualTo(1);

        Embedding e7 = new Embedding(new float[]{1.0f, 1.0f});
        Embedding e8 = new Embedding(new float[]{2.0f, 2.0f});
        assertThat(e7.cosineSimilarity(e8)).isEqualTo(1);
    }

    @Test
    public void test_relevanceScore() {
        Embedding e1 = new Embedding(new float[]{1.0f, 1.0f});
        Embedding e2 = new Embedding(new float[]{-1.0f, -1.0f});
        assertThat(e1.relevanceScore(e2)).isEqualTo(0);

        Embedding e3 = new Embedding(new float[]{1.0f, 1.0f});
        Embedding e4 = new Embedding(new float[]{1.0f, -1.0f});
        assertThat(e3.relevanceScore(e4)).isEqualTo(0.5f);

        Embedding e5 = new Embedding(new float[]{1.0f, 1.0f});
        Embedding e6 = new Embedding(new float[]{2.0f, 2.0f});
        assertThat(e5.relevanceScore(e6)).isEqualTo(1);
    }

    @Test
    public void test_from() {
        assertThat(Embedding.from(new float[]{1.0f, 2.0f, 3.0f}))
                .isEqualTo(new Embedding(new float[]{1.0f, 2.0f, 3.0f}));

        List<Float> floatList = new ArrayList<>();
        floatList.add(1.0f);
        floatList.add(2.0f);
        floatList.add(3.0f);
        assertThat(Embedding.from(floatList))
                .isEqualTo(new Embedding(new float[]{1.0f, 2.0f, 3.0f}));

        List<Double> doubleList = new ArrayList<>();
        doubleList.add(1.0);
        doubleList.add(2.0);
        doubleList.add(3.0);
        assertThat(Embedding.from(doubleList))
                .isEqualTo(new Embedding(new float[]{1.0f, 2.0f, 3.0f}));
    }

    @Test
    void test_normalize() {
        Embedding embedding = new Embedding(new float[]{6f, 8f});
        embedding.normalize();

        Embedding expect = new Embedding(new float[]{0.6f, 0.8f});
        assertThat(embedding).isEqualTo(expect);
    }

}