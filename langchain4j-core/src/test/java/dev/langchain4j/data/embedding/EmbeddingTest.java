package dev.langchain4j.data.embedding;

import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

class EmbeddingTest implements WithAssertions {
    @Test
    void equals_hash() {
        Embedding e1 = new Embedding(new float[] {1.0f, 2.0f, 3.0f});
        Embedding e2 = new Embedding(new float[] {1.0f, 2.0f, 3.0f});

        assertThat(e1)
                .isEqualTo(e1)
                .isNotEqualTo(null)
                .isNotEqualTo(new Object())
                .isEqualTo(e2)
                .hasSameHashCodeAs(e2);

        assertThat(new Embedding(new float[] {99.0f, 2.0f, 3.0f})).isNotEqualTo(e1);
        assertThat(new Embedding(new float[] {1.0f, 2.0f, 3.0f, 4.0f})).isNotEqualTo(e1);
    }

    @Test
    void accessors() {
        Embedding e1 = new Embedding(new float[] {1.0f, 2.0f, 3.0f});
        assertThat(e1.dimension()).isEqualTo(3);
        assertThat(e1.vector()).containsExactly(1.0f, 2.0f, 3.0f);
        assertThat(e1.vectorAsList()).containsExactly(1.0f, 2.0f, 3.0f);
        assertThat(e1.vectorAsDoubleArray()).containsExactly(1.0d, 2.0d, 3.0d);

        assertThat(e1).hasToString("Embedding { vector = [1.0, 2.0, 3.0] }");
    }

    @Test
    void from() {
        assertThat(Embedding.from(new float[] {1.0f, 2.0f, 3.0f}))
                .isEqualTo(new Embedding(new float[] {1.0f, 2.0f, 3.0f}));

        List<Float> list = new ArrayList<>();
        list.add(1.0f);
        list.add(2.0f);
        list.add(3.0f);
        assertThat(Embedding.from(list)).isEqualTo(new Embedding(new float[] {1.0f, 2.0f, 3.0f}));
    }

    @Test
    void normalize() {
        Embedding embedding = new Embedding(new float[] {6f, 8f});
        embedding.normalize();

        Embedding expect = new Embedding(new float[] {0.6f, 0.8f});
        assertThat(embedding).isEqualTo(expect);
    }

    @Test
    void normalize_large_finite_values() {
        Embedding embedding = new Embedding(new float[] {6e20f, -8e20f});

        embedding.normalize();

        assertThat(embedding.vector()[0]).isCloseTo(0.6f, within(1e-6f));
        assertThat(embedding.vector()[1]).isCloseTo(-0.8f, within(1e-6f));
    }

    @Test
    void normalize_when_norm_exceeds_float_range() {
        Embedding embedding = new Embedding(new float[] {Float.MAX_VALUE, Float.MAX_VALUE});

        embedding.normalize();

        float expected = (float) (1 / Math.sqrt(2));
        assertThat(embedding.vector()[0]).isCloseTo(expected, within(1e-6f));
        assertThat(embedding.vector()[1]).isCloseTo(expected, within(1e-6f));
    }

    @Test
    void normalize_zero() {
        Embedding embedding = new Embedding(new float[] {0f, 0f});
        embedding.normalize();

        Embedding expect = new Embedding(new float[] {0f, 0f});
        assertThat(embedding).isEqualTo(expect);
    }
}
