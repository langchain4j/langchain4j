package dev.langchain4j.model.embedding;

import dev.langchain4j.data.embedding.Embedding;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static dev.langchain4j.internal.Utils.repeat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ALL_MINILM_L6_V2_EmbeddingModelTest {

    EmbeddingModel model = new ALL_MINILM_L6_V2_EmbeddingModel();

    @Test
    @Disabled("Temporary disabling. This test should run only when this or used (e.g. langchain4j-embeddings) module(s) change")
    void should_embed() {

        Embedding first = model.embed("hi");
        assertThat(first.vector()).hasSize(384);

        Embedding second = model.embed("hello");
        assertThat(second.vector()).hasSize(384);

        assertThat(Similarity.cosine(first.vector(), second.vector())).isGreaterThan(0.8);
    }

    @Test
    @Disabled("Temporary disabling. This test should run only when this or used (e.g. langchain4j-embeddings) module(s) change")
    void should_embed_510_token_long_text() {

        String oneToken = "hello ";

        Embedding embedding = model.embed(repeat(oneToken, 510));

        assertThat(embedding.vector()).hasSize(384);
    }

    @Test
    @Disabled("Temporary disabling. This test should run only when this or used (e.g. langchain4j-embeddings) module(s) change")
    void should_fail_to_embed_511_token_long_text() {

        String oneToken = "hello ";

        assertThatThrownBy(() -> model.embed(repeat(oneToken, 511)))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Cannot embed text longer than 510 tokens. The following text is 511 tokens long: hello hello");
    }
}