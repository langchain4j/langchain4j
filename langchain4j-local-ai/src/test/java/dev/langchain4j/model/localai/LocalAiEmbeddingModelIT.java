package dev.langchain4j.model.localai;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocalAiEmbeddingModelIT extends AbstractLocalAiInfrastructure {

    @Test
    void should_embed() {

        EmbeddingModel model = LocalAiEmbeddingModel.builder()
                .baseUrl(localAi.getBaseUrl())
                .modelName("all-minilm-l6-v2")
                .logRequests(true)
                .logResponses(true)
                .build();

        Embedding embedding = model.embed("hello").content();

        assertThat(embedding.vector()).hasSize(384);
    }
}