package dev.langchain4j.model.ollama;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.langchain4j.model.ollama.OllamaImage.TINY_DOLPHIN_MODEL;
import static org.assertj.core.api.Assertions.assertThat;

class OllamaModelsIT extends AbstractOllamaLanguageModelInfrastructure {

    OllamaModels ollamaModels = OllamaModels.builder()
            .baseUrl(ollama.getEndpoint())
            .logRequests(true)
            .logResponses(true)
            .build();

    @Test
    void should_return_ollama_models_list() {
        // given AbstractOllamaInfrastructure

        // when
        Response<List<OllamaModel>> response = ollamaModels.availableModels();

        // then
        assertThat(response.content().size()).isGreaterThan(0);
        assertThat(response.content().get(0).getName()).contains(TINY_DOLPHIN_MODEL);
    }

    @Test
    void should_return_ollama_model_info_for_given_ollama_model() {
        // given AbstractOllamaInfrastructure

        // when
        OllamaModel ollamaModel = OllamaModel.builder()
                .name(TINY_DOLPHIN_MODEL)
                .build();

        Response<OllamaModelCard> response = ollamaModels.modelCard(ollamaModel);

        // then
        assertThat(response.content().getModelfile()).isNotBlank();
        assertThat(response.content().getTemplate()).isNotBlank();
        assertThat(response.content().getParameters()).isNotBlank();
        assertThat(response.content().getModifiedAt()).isNotNull();
        assertThat(response.content().getDetails().getFamily()).isEqualTo("llama");
    }

    @Test
    void should_return_ollama_model_info_for_given_ollama_model_name() {
        // given AbstractOllamaInfrastructure

        // when
        Response<OllamaModelCard> response = ollamaModels.modelCard(TINY_DOLPHIN_MODEL);

        // then
        assertThat(response.content().getModelfile()).isNotBlank();
        assertThat(response.content().getTemplate()).isNotBlank();
        assertThat(response.content().getParameters()).isNotBlank();
        assertThat(response.content().getModifiedAt()).isNotNull();
        assertThat(response.content().getModelInfo().keySet().size()).isPositive();
        assertThat(response.content().getModelInfo().containsKey("general.architecture")).isTrue();
        assertThat(response.content().getDetails().getFamily()).isEqualTo("llama");
    }

    @Test
    void should_return_list_of_running_models() {
        // given AbstractOllamaInfrastructure

        // load model
        ChatLanguageModel model = OllamaChatModel.builder()
                .baseUrl(ollama.getEndpoint())
                .modelName(TINY_DOLPHIN_MODEL)
                .temperature(0.0)
                .numPredict(1)
                .build();
        model.generate("Tell a joke");

        // when
        Response<List<RunningOllamaModel>> response = ollamaModels.runningModels();

        // then
        RunningOllamaModel runningOllamaModel = response.content().get(0);

        assertThat(runningOllamaModel.getName()).contains(TINY_DOLPHIN_MODEL);
        assertThat(runningOllamaModel.getDigest()).isNotBlank();
        assertThat(runningOllamaModel.getExpiresAt()).isNotNull();
    }
}