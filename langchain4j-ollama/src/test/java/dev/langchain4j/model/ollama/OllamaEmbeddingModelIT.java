package dev.langchain4j.model.ollama;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OllamaEmbeddingModelIT extends AbstractOllamaInfrastructure {

    EmbeddingModel model = OllamaEmbeddingModel.builder()
            .baseUrl(getBaseUrl())
            .modelName(MODEL)
            .build();

    @Test
    void should_embed() {

        // given
        String text = "hello world";

        // when
        Response<Embedding> response = model.embed(text);
        System.out.println(response);

        // then
        assertThat(response.content().vector()).isNotEmpty();

        assertThat(response.tokenUsage()).isNull();
        assertThat(response.finishReason()).isNull();
    }
}
