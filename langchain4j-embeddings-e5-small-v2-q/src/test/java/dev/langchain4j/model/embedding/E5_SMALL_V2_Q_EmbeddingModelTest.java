package dev.langchain4j.model.embedding;

import dev.langchain4j.data.embedding.Embedding;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class E5_SMALL_V2_Q_EmbeddingModelTest {

    @Test
    @Disabled("Temporary disabling. This test should run only when this or used (e.g. langchain4j-embeddings) module(s) change")
    void should_embed() {

        EmbeddingModel model = new E5_SMALL_V2_Q_EmbeddingModel();

        Embedding first = model.embed("hi");
        assertThat(first.vector()).hasSize(384);

        Embedding second = model.embed("hello");
        assertThat(second.vector()).hasSize(384);

        assertThat(Similarity.cosine(first.vector(), second.vector())).isGreaterThan(0.95);
    }
}