package dev.langchain4j.model.localai;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocalAiEmbeddingModelIT {

    EmbeddingModel model = LocalAiEmbeddingModel.builder()
            .baseUrl("http://localhost:8082/v1")
            .modelName("text-embedding-ada-002")
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
        assertThat(embedding.dimension()).isEqualTo(2048);

        assertThat(response.tokenUsage()).isNull();
        assertThat(response.finishReason()).isNull();
    }
}
