package dev.langchain4j.model.ollama;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;

import static dev.langchain4j.model.ollama.OllamaImage.ALL_MINILM_MODEL;
import static org.assertj.core.api.Assertions.assertThat;

class OllamaEmbeddingModelIT extends AbstractOllamaEmbeddingModelInfrastructure {

    EmbeddingModel model = OllamaEmbeddingModel.builder()
            .baseUrl(ollama.getEndpoint())
            .modelName(ALL_MINILM_MODEL)
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

    @Test
    void should_return_correct_dimension() {
        // given
        String text = "hello world";

        // when
        Response<Embedding> response = model.embed(text);

        // then
        assertThat(model.dimension()).isEqualTo(response.content().dimension());
    }
}
