package dev.langchain4j.model.azure;

import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;

import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_KEY", matches = ".+")
class AzureOpenAiLanguageModelIT {

    Logger logger = LoggerFactory.getLogger(AzureOpenAiLanguageModelIT.class);

    LanguageModel model = AzureModelBuilders.languageModelBuilder()
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

    @ParameterizedTest(name = "Testing model {0}")
    @EnumSource(
            value = AzureOpenAiLanguageModelName.class,
            mode = EXCLUDE,
            names = {"GPT_3_5_TURBO_INSTRUCT", "TEXT_DAVINCI_002", "TEXT_DAVINCI_002_1"})
    void should_support_all_string_model_names(AzureOpenAiLanguageModelName modelName) {

        // given
        String modelNameString = modelName.toString();

        LanguageModel model = AzureModelBuilders.languageModelBuilder()
                .deploymentName(modelNameString)
                .maxTokens(1) // to save tokens
                .logRequestsAndResponses(true)
                .build();

        // when
        String prompt = "Describe the capital of France in 50 words: ";
        Response<String> response = model.generate(prompt);

        // then
        assertThat(response.finishReason()).isEqualTo(LENGTH);
    }
}
