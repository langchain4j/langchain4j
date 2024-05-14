package dev.langchain4j.model.ollama;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class OllamaClientIT extends AbstractOllamaLanguageModelInfrastructure {

    @Test
    void should_respond_with_models_list() {
        // given AbstractOllamaInfrastructure

        // when
        OllamaClient ollamaClient = OllamaClient.builder()
                .baseUrl(ollama.getEndpoint())
                .timeout(Duration.ofMinutes(1))
                .build();

        ModelsListResponse modelListResponse = ollamaClient.listModels();

        // then
        assertThat(modelListResponse.getModels().size()).isGreaterThan(0);
        assertThat(modelListResponse.getModels().get(0).getName()).isEqualTo("tinydolphin:latest");
        assertThat(modelListResponse.getModels().get(0).getDigest()).isNotNull();
        assertThat(modelListResponse.getModels().get(0).getSize()).isPositive();
    }

    @Test
    void should_respond_with_model_information() {
        // given AbstractOllamaInfrastructure

        // when
        OllamaClient ollamaClient = OllamaClient.builder()
                .baseUrl(ollama.getEndpoint())
                .timeout(Duration.ofMinutes(1))
                .build();

        OllamaModelCard modelDetailsResponse = ollamaClient.showInformation(ShowModelInformationRequest.builder()
                .name("tinydolphin:latest")
                .build());

        // then
        assertThat(modelDetailsResponse.getModelfile()).contains("# Modelfile generate by \"ollama show\"");
        assertThat(modelDetailsResponse.getParameters()).contains("stop");
        assertThat(modelDetailsResponse.getTemplate()).contains("<|im_start|>");
        assertThat(modelDetailsResponse.getDetails().getFormat()).isEqualTo("gguf");
        assertThat(modelDetailsResponse.getDetails().getFamily()).isEqualTo("llama");
    }
}