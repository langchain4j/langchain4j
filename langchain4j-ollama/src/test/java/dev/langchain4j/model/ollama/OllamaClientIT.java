package dev.langchain4j.model.ollama;

import dev.langchain4j.http.HttpClientBuilderLoader;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OllamaClientIT extends AbstractOllamaLanguageModelInfrastructure {

    @Test
    void should_respond_with_models_list() {
        // given AbstractOllamaInfrastructure

        // when
        OllamaClient ollamaClient = OllamaClient.builder()
                .baseUrl(ollamaBaseUrl(ollama))
                .timeout(Duration.ofMinutes(1))
                .build();

        ModelsListResponse modelListResponse = ollamaClient.listModels();

        // then
        List<OllamaModel> ollamaModels = modelListResponse.getModels();
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
    void should_respond_with_model_information() {
        // given AbstractOllamaInfrastructure

        // when
        OllamaClient ollamaClient = OllamaClient.builder()
                .baseUrl(ollamaBaseUrl(ollama))
                .timeout(Duration.ofMinutes(1))
                .build();

        OllamaModelCard modelDetailsResponse = ollamaClient.showInformation(ShowModelInformationRequest.builder()
                .name("tinydolphin:latest")
                .build());

        // then
        assertThat(modelDetailsResponse.getModelfile()).contains("# Modelfile generated by \"ollama show\"");
        assertThat(modelDetailsResponse.getParameters()).contains("stop");
        assertThat(modelDetailsResponse.getTemplate()).contains("<|im_start|>");
        assertThat(modelDetailsResponse.getDetails().getFormat()).isEqualTo("gguf");
        assertThat(modelDetailsResponse.getDetails().getFamily()).isEqualTo("llama");
    }
}
