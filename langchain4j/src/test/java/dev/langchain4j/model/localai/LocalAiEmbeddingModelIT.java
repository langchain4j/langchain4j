package dev.langchain4j.model.localai;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocalAiEmbeddingModelIT {

    @Test
    @Disabled("until we host LocalAI instance somewhere")
    void should_embed() {

        EmbeddingModel model = LocalAiEmbeddingModel.builder()
                .baseUrl("http://localhost:8080")
                .modelName("ggml-model-q4_0")
                .logRequests(true)
                .logResponses(true)
                .build();

        Embedding embedding = model.embed("hello").content();

        assertThat(embedding.vector()).hasSize(384);
    }
}