package dev.langchain4j.model.azure;

import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import static dev.langchain4j.model.output.FinishReason.STOP;
import static org.assertj.core.api.Assertions.assertThat;

class AzureOpenAiLanguageModelIT {

    @Test
    void should_generate_answer_and_return_token_usage_and_finish_reason_stop() {

        LanguageModel model = AzureOpenAiLanguageModel.builder()
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .serviceVersion(System.getenv("AZURE_OPENAI_SERVICE_VERSION"))
                .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                .deploymentName(System.getenv("AZURE_OPENAI_DEPLOYMENT_NAME"))
                .logRequests(true)
                .maxTokens(1000)
                .build();

        String prompt = "Hello, how are you?";

        Response<String> response = model.generate(prompt);
        System.out.println(response);

        assertThat(response.content()).isNotBlank();

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(6);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);
    }
}