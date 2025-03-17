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
    void normalize_zero() {
        Embedding embedding = new Embedding(new float[] {0f, 0f});
        embedding.normalize();

        Embedding expect = new Embedding(new float[] {0f, 0f});
        assertThat(embedding).isEqualTo(expect);
    }
}
