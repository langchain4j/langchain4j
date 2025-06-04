package dev.langchain4j.model.ollama;

import static dev.langchain4j.model.chat.request.ResponseFormat.JSON;
import static dev.langchain4j.model.ollama.OllamaImage.TINY_DOLPHIN_MODEL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;

class OllamaLanguageModelIT extends AbstractOllamaLanguageModelInfrastructure {

    LanguageModel model = OllamaLanguageModel.builder()
            .baseUrl(ollamaBaseUrl(ollama))
            .modelName(TINY_DOLPHIN_MODEL)
            .temperature(0.0)
            .build();

    @Test
    void should_generate_answer() {

        // given
        String userMessage = "What is the capital of Germany?";

        // when
        Response<String> response = model.generate(userMessage);

        // then
        assertThat(response.content()).contains("Berlin");
    }

    @Test
    void should_respect_numPredict() {

        // given
        int numPredict = 1; // max output tokens

        LanguageModel model = OllamaLanguageModel.builder()
                .baseUrl(ollamaBaseUrl(ollama))
                .modelName(TINY_DOLPHIN_MODEL)
                .numPredict(numPredict)
                .temperature(0.0)
                .build();

        String prompt = "What is the capital of Germany?";

        // when
        Response<String> response = model.generate(prompt);

        // then
        assertThat(response.content()).doesNotContain("Berlin");
        assertThat(response.tokenUsage().outputTokenCount()).isBetween(numPredict, numPredict + 2); // bug in Ollama
    }

    @Test
    void should_generate_valid_json() {

        // given
        LanguageModel model = OllamaLanguageModel.builder()
                .baseUrl(ollamaBaseUrl(ollama))
                .modelName(TINY_DOLPHIN_MODEL)
                .responseFormat(JSON)
                .temperature(0.0)
                .build();

        String userMessage = "Return JSON with two fields: name and age of John Doe, 42 years old.";

        // when
        Response<String> response = model.generate(userMessage);

        // then
        assertThat(response.content()).isEqualToIgnoringWhitespace("{\"name\": \"John Doe\", \"age\": 42}");
    }
}
