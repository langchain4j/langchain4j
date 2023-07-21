package dev.langchain4j.model.embedding;

import dev.langchain4j.data.embedding.Embedding;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ALL_MINILM_L6_V2_Q_EmbeddingModelTest {

    @Test
    void should_embed() {

        EmbeddingModel model = new ALL_MINILM_L6_V2_Q_EmbeddingModel();

        Embedding first = model.embed("hi");
        assertThat(first.vector()).hasSize(384);

        Embedding second = model.embed("hello");
        assertThat(second.vector()).hasSize(384);

        assertThat(Similarity.cosine(first.vector(), second.vector())).isGreaterThan(0.8);
    }
}