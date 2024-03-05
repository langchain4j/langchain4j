package dev.langchain4j.model.ollama;

import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OllamaLanguageModelIT extends AbstractOllamaInfrastructure {

    LanguageModel model = OllamaLanguageModel.builder()
            .baseUrl(getBaseUrl())
            .modelName(MODEL)
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

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(38);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isNull();
    }

    @Test
    void should_respect_numPredict() {

        // given
        int numPredict = 1; // max output tokens

        LanguageModel model = OllamaLanguageModel.builder()
                .baseUrl(getBaseUrl())
                .modelName(MODEL)
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
                .baseUrl(getBaseUrl())
                .modelName(MODEL)
                .format("json")
                .temperature(0.0)
                .build();

        String userMessage = "Return JSON with two fields: name and age of John Doe, 42 years old.";

        // when
        Response<String> response = model.generate(userMessage);

        // then
        assertThat(response.content()).isEqualToIgnoringWhitespace("{\"name\": \"John Doe\", \"age\": 42}");
    }
}
