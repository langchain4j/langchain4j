package dev.langchain4j.model.ark;

import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import static dev.langchain4j.model.output.FinishReason.STOP;
import static org.assertj.core.api.Assertions.assertThat;

class ArkLanguageModelIT {

    LanguageModel model = ArkLanguageModel.builder()
            .apiKey(System.getenv("ARK_API_KEY"))
            .model(System.getenv("ARK_ENDPOINT_ID"))
            .temperature(0.0)
            .build();

    @Test
    void should_generate_answer_and_return_token_usage_and_finish_reason_stop() {

        String prompt = "What is the capital of China?";

        Response<String> response = model.generate(prompt);
        System.out.println(response);

        assertThat(response.content()).contains("Beijing");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(15);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);
    }
}