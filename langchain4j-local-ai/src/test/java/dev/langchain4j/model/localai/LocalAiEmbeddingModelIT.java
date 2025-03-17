package dev.langchain4j.model.localai;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocalAiEmbeddingModelIT extends AbstractLocalAiInfrastructure {

    EmbeddingModel model = LocalAiEmbeddingModel.builder()
            .baseUrl(localAi.getBaseUrl())
            .modelName("ggml-model-q4_0")
            .logRequests(true)
            .logResponses(true)
            .build();

    @Test
    void should_embed_text() {

        // given
        String text = "hello";

        // when
        Response<Embedding> response = model.embed(text);

        // then
        Embedding embedding = response.content();
        assertThat(embedding.dimension()).isEqualTo(384);

        assertThat(response.tokenUsage()).isNull();
        assertThat(response.finishReason()).isNull();
    }
}