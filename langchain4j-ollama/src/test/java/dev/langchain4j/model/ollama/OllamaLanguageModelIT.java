package dev.langchain4j.model.ollama;

import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OllamaLanguageModelIT extends AbstractOllamaInfrastructure {

    LanguageModel model = OllamaLanguageModel.builder()
            .baseUrl(getBaseUrl())
            .modelName(ORCA_MINI_MODEL)
            .build();

    @Test
    void should_generate_answer() {

        String prompt = "Hello, how are you?";

        Response<String> response = model.generate(prompt);
        System.out.println(response);

        assertThat(response.content()).isNotBlank();
        assertThat(response.tokenUsage()).isNotNull();
    }
}
