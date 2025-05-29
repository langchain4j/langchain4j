package dev.langchain4j.model.ollama;

import static dev.langchain4j.model.ollama.OllamaImage.TINY_DOLPHIN_MODEL;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.output.Response;
import java.util.List;
import org.junit.jupiter.api.Test;

class OllamaModelsIT extends AbstractOllamaLanguageModelInfrastructure {

    OllamaModels ollamaModels = OllamaModels.builder()
            .baseUrl(ollamaBaseUrl(ollama))
            .logRequests(true)
            .logResponses(true)
            .build();

    @Test
    void should_return_ollama_models_list() {
        // given AbstractOllamaInfrastructure

        // when
        Response<List<OllamaModel>> response = ollamaModels.availableModels();

        // then
        List<OllamaModel> ollamaModels = response.content();
        assertThat(ollamaModels).isNotEmpty();
        for (OllamaModel ollamaModel : ollamaModels) {
            assertThat(ollamaModel.getName()).isNotBlank();
            assertThat(ollamaModel.getSize()).isPositive();
            assertThat(ollamaModel.getDigest()).isNotBlank();
            assertThat(ollamaModel.getDetails()).isNotNull(); // TODO assert internals
            assertThat(ollamaModel.getModel()).isNotBlank();
            assertThat(ollamaModel.getModifiedAt()).isNotNull();
        }
    }

    @Test
    void should_return_ollama_model_info_for_given_ollama_model() {
        // given AbstractOllamaInfrastructure

        // when
        OllamaModel ollamaModel = OllamaModel.builder().name(TINY_DOLPHIN_MODEL).build();

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
        assertThat(response.content().getModelInfo()).containsKey("general.architecture");
        assertThat(response.content().getDetails().getFamily()).isEqualTo("llama");
    }

    @Test
    void should_return_list_of_running_models() {
        // given AbstractOllamaInfrastructure

        // load model
        ChatModel model = OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl(ollama))
                .modelName(TINY_DOLPHIN_MODEL)
                .temperature(0.0)
                .numPredict(1)
                .build();
        model.chat("Tell a joke");

        // when
        Response<List<RunningOllamaModel>> response = ollamaModels.runningModels();

        // then
        RunningOllamaModel runningOllamaModel = response.content().get(0);

        assertThat(runningOllamaModel.getName()).contains(TINY_DOLPHIN_MODEL);
        assertThat(runningOllamaModel.getDigest()).isNotBlank();
        assertThat(runningOllamaModel.getExpiresAt()).isNotNull();
    }
}
