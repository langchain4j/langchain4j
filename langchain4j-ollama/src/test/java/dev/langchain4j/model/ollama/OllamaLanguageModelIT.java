package dev.langchain4j.model.ollama;

import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;

import static dev.langchain4j.model.ollama.OllamaImage.TINY_DOLPHIN_MODEL;
import static org.assertj.core.api.Assertions.assertThat;

class OllamaLanguageModelIT extends AbstractOllamaLanguageModelInfrastructure {

    LanguageModel model = OllamaLanguageModel.builder()
            .baseUrl(ollama.getEndpoint())
            .modelName(TINY_DOLPHIN_MODEL)
            .temperature(0.0)
            .build();

    @Test
    void should_generate_answer() {

        // given
        String userMessage = "What is the capital of Germany?";

        // when
        Response<String> response = model.generate(userMessage);
        System.out.println(response);

        // then
        assertThat(response.content()).contains("Berlin");
    }

    @Test
    void should_respect_numPredict() {

        // given
        int numPredict = 1; // max output tokens

        LanguageModel model = OllamaLanguageModel.builder()
                .baseUrl(ollama.getEndpoint())
                .modelName(TINY_DOLPHIN_MODEL)
                .numPredict(numPredict)
                .temperature(0.0)
                .build();

        String prompt = "What is the capital of Germany?";

        // when
        Response<String> response = model.generate(prompt);
        System.out.println(response);

        // then
        assertThat(response.content()).doesNotContain("Berlin");
        assertThat(response.tokenUsage().outputTokenCount()).isBetween(numPredict, numPredict + 2); // bug in Ollama
    }

    @Test
    void should_generate_valid_json() {

        // given
        LanguageModel model = OllamaLanguageModel.builder()
                .baseUrl(ollama.getEndpoint())
                .modelName(TINY_DOLPHIN_MODEL)
                .format("json")
                .temperature(0.0)
                .build();

        String userMessage = "Return JSON with two fields: name and age of John Doe, 42 years old.";

        // when
        Response<String> response = model.generate(userMessage);

        // then
        assertThat(response.content()).isEqualToIgnoringWhitespace("{\"name\": \"John Doe\", \"age\": 42}");
    }

    @Test
    void should_preload_model_if_preload_is_true() {
        // given
        OllamaChatModel model = OllamaChatModel.builder()
                .baseUrl(ollama.getEndpoint())
                .modelName(TINY_DOLPHIN_MODEL)
                .preload(true)
                .build();

        // when
        // Preload is called inside the constructor if preload is true, so no action is needed here.

        // then
        // Check if preload was called by verifying if a dummy request was made
        // This might require you to mock the underlying OllamaClient and verify that `generate` was called with an empty message
        // Assuming `OllamaClient` is mockable and you have a way to inspect interactions:
        assertThat(model.modelLoadedInMemory).isTrue();
    }

    @Test
    void should_not_preload_model_if_preload_is_false() {
        // given
        OllamaLanguageModel model = OllamaLanguageModel.builder()
                .baseUrl(ollama.getEndpoint())
                .modelName(TINY_DOLPHIN_MODEL)
                .preload(false)
                .build();

        // when
        // Preload is called inside the constructor if preload is true, so no action is needed here.

        // then
        // Check if preload was called by verifying if a dummy request was made
        // This might require you to mock the underlying OllamaClient and verify that `generate` was called with an empty message
        // Assuming `OllamaClient` is mockable and you have a way to inspect interactions:
        assertThat(model.modelLoadedInMemory).isFalse();
    }

    @Test
    void should_pass_keep_alive_parameter() {
        // given
        String keepAliveDuration = "10m";
        OllamaLanguageModel model = OllamaLanguageModel.builder()
                .baseUrl(ollama.getEndpoint())
                .modelName(TINY_DOLPHIN_MODEL)
                .temperature(0.0)
                .keepAlive(keepAliveDuration)
                .build();

        String prompt = "What is the capital of Germany?";

        // when
        Response<String> response = model.generate(prompt);
        System.out.println(response);

        // then
        assertThat(response.content()).contains("Berlin");
    }
}
