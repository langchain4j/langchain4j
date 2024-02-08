package dev.langchain4j.model.ollama;

import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OllamaModelsIT extends AbstractOllamaInfrastructure {

    OllamaModels ollamaModels = OllamaModels.builder()
            .baseUrl(getBaseUrl())
            .build();

    @Test
    void should_return_ollama_models_list() {
        // given AbstractOllamaInfrastructure

        // when
        Response<List<OllamaModel>> response = ollamaModels.availableModels();

        // then
        assertThat(response.content().size()).isGreaterThan(0);
        assertThat(response.content().get(0).getName()).isEqualTo("phi:latest");
    }

    @Test
    void should_return_ollama_model_info_for_given_ollama_model() {
        // given AbstractOllamaInfrastructure

        // when
        OllamaModel ollamaModel = OllamaModel.builder()
                .name("phi:latest")
                .build();

        Response<OllamaModelCard> response = ollamaModels.modelCard(ollamaModel);

        // then
        assertThat(response.content().getModelfile()).isNotBlank();
        assertThat(response.content().getTemplate()).isNotBlank();
        assertThat(response.content().getParameters()).isNotBlank();
        assertThat(response.content().getDetails().getFamily()).isEqualTo("phi2");
    }

    @Test
    void should_return_ollama_model_info_for_given_ollama_model_name() {
        // given AbstractOllamaInfrastructure

        // when
        Response<OllamaModelCard> response = ollamaModels.modelCard("phi:latest");

        // then
        assertThat(response.content().getModelfile()).isNotBlank();
        assertThat(response.content().getTemplate()).isNotBlank();
        assertThat(response.content().getParameters()).isNotBlank();
        assertThat(response.content().getDetails().getFamily()).isEqualTo("phi2");
    }

}