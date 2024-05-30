package dev.langchain4j.model.azure;

import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static dev.langchain4j.model.openai.OpenAiLanguageModelName.GPT_3_5_TURBO_INSTRUCT;
import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static org.assertj.core.api.Assertions.assertThat;

class AzureOpenAiLanguageModelIT {

    Logger logger = LoggerFactory.getLogger(AzureOpenAiLanguageModelIT.class);

    LanguageModel model = AzureOpenAiLanguageModel.builder()
            .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
            .apiKey(System.getenv("AZURE_OPENAI_KEY"))
            .deploymentName("gpt-35-turbo-instruct")
            .tokenizer(new OpenAiTokenizer(GPT_3_5_TURBO_INSTRUCT))
            .temperature(0.0)
            .maxTokens(20)
            .logRequestsAndResponses(true)
            .build();

    @Test
    void should_generate_answer_and_return_token_usage_and_finish_reason_stop() {
        String prompt = "The capital of France is: ";

        Response<String> response = model.generate(prompt);
        logger.info(response.toString());

        assertThat(response.content()).containsIgnoringCase("Paris");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(7);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_generate_answer_and_finish_reason_length() {
        String prompt = "Describe the capital of France in 100 words: ";

        Response<String> response = model.generate(prompt);
        logger.info(response.toString());

        assertThat(response.finishReason()).isEqualTo(LENGTH);
    }
}