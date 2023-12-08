package dev.langchain4j.model.localai;

import dev.langchain4j.model.language.LanguageModel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocalAiLanguageModelIT extends AbstractLocalAiInfrastructure {

    @Test
    void should_send_prompt_and_return_answer() {

        LanguageModel model = LocalAiLanguageModel.builder()
                .baseUrl(localAi.getBaseUrl())
                .modelName("ggml-gpt4all-j")
                .maxTokens(3)
                .logRequests(true)
                .logResponses(true)
                .build();

        String answer = model.generate("Say 'hello'").content();

        assertThat(answer).isNotBlank();
        System.out.println(answer);
    }
}