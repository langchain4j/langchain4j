package dev.langchain4j.model.openai;

import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.output.Result;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import static dev.langchain4j.model.output.FinishReason.STOP;
import static org.assertj.core.api.Assertions.assertThat;

class OpenAiLanguageModelIT {

    LanguageModel model = OpenAiLanguageModel.builder()
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .logRequests(true)
            .logResponses(true)
            .build();

    @Test
    void should_generate_answer_and_return_token_usage_and_finish_reason_stop() {

        String prompt = "Hello, how are you?";

        Result<String> result = model.generate(prompt);

        assertThat(result.get()).isNotBlank();

        TokenUsage tokenUsage = result.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(6);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(result.finishReason()).isEqualTo(STOP);
    }
}