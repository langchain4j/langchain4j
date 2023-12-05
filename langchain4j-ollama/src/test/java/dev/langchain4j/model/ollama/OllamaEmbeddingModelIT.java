package dev.langchain4j.model.ollama;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OllamaEmbeddingModelIT extends AbstractOllamaInfrastructure {

    EmbeddingModel model = OllamaEmbeddingModel.builder()
            .baseUrl(getBaseUrl())
            .modelName(ORCA_MINI_MODEL)
            .build();

    @Test
    void should_embed() {

        Response<Embedding> response = model.embed("hello world");
        System.out.println(response);

       assertThat(response.content().vector()).isNotEmpty();
    }
}
