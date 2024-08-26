package dev.langchain4j.model.localai;

import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;

import static dev.langchain4j.model.output.FinishReason.STOP;
import static org.assertj.core.api.Assertions.assertThat;

class LocalAiLanguageModelIT extends AbstractLocalAiInfrastructure {

    LanguageModel model = LocalAiLanguageModel.builder()
            .baseUrl(localAi.getBaseUrl())
            .modelName("ggml-gpt4all-j")
            .maxTokens(3)
            .logRequests(true)
            .logResponses(true)
            .build();

    @Test
    void should_send_prompt_and_return_response() {

        // given
        String prompt = "hello";

        // when
        Response<String> response = model.generate(prompt);

        // then
        assertThat(response.content()).isNotBlank();

        assertThat(response.tokenUsage()).isNull();
        assertThat(response.finishReason()).isEqualTo(STOP); // should be LENGTH, this is a bug in LocalAI
    }
}