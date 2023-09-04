package dev.langchain4j.model.openai;

import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.output.Result;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import static dev.langchain4j.model.output.FinishReason.STOP;
import static org.assertj.core.api.Assertions.assertThat;

class OpenAiLanguageModelIT {

    @Test
    void should_generate_answer_and_return_token_usage_and_finish_reason_stop() {

        LanguageModel model = OpenAiLanguageModel.withApiKey(System.getenv("OPENAI_API_KEY"));
        String prompt = "hello, how are you?";

        Result<String> result = model.generate(prompt);
        System.out.println(result.get());

        assertThat(result.get()).isNotBlank();

        TokenUsage tokenUsage = result.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(6);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(1);
        assertThat(tokenUsage.totalTokenCount()).isGreaterThan(7);

        assertThat(result.finishReason()).isEqualTo(STOP);
    }
}