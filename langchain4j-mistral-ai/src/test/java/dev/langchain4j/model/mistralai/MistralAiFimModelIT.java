package dev.langchain4j.model.mistralai;

import static dev.langchain4j.model.output.FinishReason.STOP;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import java.util.List;

class MistralAiFimModelIT {

    LanguageModel codestral = MistralAiFimModel.builder()
            .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
            .modelName(MistralAiFimModelName.CODESTRAL_LATEST)
            .logRequests(true)
            .logResponses(true)
            .build();

    @Test
    void should_generate_code_completion_and_return_token_usage_and_finish_reason_stop() {

        // Given
        String codePrompt = "public static void main(";

        // When
        Response<String> response = codestral.generate(codePrompt);

        // Then
        assertThat(response.content()).contains("String[] args");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_generate_code_completion_with_suffix() {

        // Given
        MistralAiFimModel codestral = MistralAiFimModel.builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .modelName(MistralAiFimModelName.CODESTRAL_LATEST)
                .logRequests(true)
                .build();
        String codePrompt = """
                            public static void main(String[] args) {
                                // Create a function to multiply two numbers
                          """;
        String suffix = """
                          System.out.println(result);
                        }
                      """;

        // When
        Response<String> response = codestral.generate(codePrompt, suffix);

        // Then
        assertThat(response.content()).isNotBlank();
        assertThat(response.content()).doesNotContainIgnoringCase(codePrompt);
        assertThat(response.content()).doesNotContainIgnoringCase(suffix);
        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_generate_code_completion_with_stop_tokens() {

        // given
        List<String> stop = List.of("{"); // must stop at the first occurrence of "{"

        MistralAiFimModel codestral = MistralAiFimModel.builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .modelName(MistralAiFimModelName.CODESTRAL_LATEST)
                .stop(stop)
                .logRequests(true)
                .build();

        String codePrompt = """
                            public static void main
                          """;

        // when
        Response<String> response = codestral.generate(codePrompt);

        // then
        assertThat(response.content()).isNotBlank();
        assertThat(response.content()).doesNotContainIgnoringCase(codePrompt);
        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_generate_code_completion_with_suffix_and_max_min_tokens() {

        // Given
        MistralAiFimModel codestral = MistralAiFimModel.builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .modelName(MistralAiFimModelName.CODESTRAL_LATEST)
                .maxTokens(1024)
                .minTokens(0)
                .logRequests(true)
                .build();

        String codePrompt = """
                          public class HelloWorld {
                            public static void main(String[] args) {
                                ChatLanguageModel model = MistralAiChatModel.withApiKey(ApiKeys.MISTRALAI_API_KEY);
                          """;
        String suffix = """
                          System.out.println(response);
                        }
                      }
                      """;

        // When
        Response<String> response = codestral.generate(codePrompt, suffix);

        // Then
        assertThat(response.content()).isNotBlank();
        assertThat(response.content()).doesNotContainIgnoringCase(codePrompt);
        assertThat(response.content()).doesNotContainIgnoringCase(suffix);
        assertThat(response.finishReason()).isEqualTo(STOP);
    }
}
