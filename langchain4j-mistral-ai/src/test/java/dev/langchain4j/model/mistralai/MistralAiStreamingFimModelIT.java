package dev.langchain4j.model.mistralai;

import static dev.langchain4j.model.output.FinishReason.STOP;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.chat.TestStreamingResponseHandler;
import dev.langchain4j.model.language.StreamingLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import java.util.List;

class MistralAiStreamingFimModelIT {

    StreamingLanguageModel codestralStream = MistralAiStreamingFimModel.builder()
            .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
            .modelName(MistralAiFimModelName.CODESTRAL_LATEST)
            .logRequests(true)
            .logResponses(true)
            .build();

    @Test
    void should_stream_code_completion_and_return_token_usage_and_finish_reason_length() {

        // Given
        String codePrompt = "public static void main(";

        // When
        TestStreamingResponseHandler<String> handler = new TestStreamingResponseHandler<>();
        codestralStream.generate(codePrompt, handler);

        Response<String> response = handler.get();

        // Then
        assertThat(response.content()).contains("String[]");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_stream_generate_code_completion_with_suffix() {

        // Given
        MistralAiStreamingFimModel codestral = MistralAiStreamingFimModel.builder()
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
        TestStreamingResponseHandler<String> handler = new TestStreamingResponseHandler<>();
        codestral.generate(codePrompt, suffix, handler);

        Response<String> response = handler.get();

        // Then
        assertThat(response.content()).isNotBlank();
        assertThat(response.content()).doesNotContainIgnoringCase(codePrompt);
        assertThat(response.content()).doesNotContainIgnoringCase(suffix);
        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_stream_generate_code_completion_with_stop_tokens() {

        // Given
        MistralAiStreamingFimModel codestral = MistralAiStreamingFimModel.builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .modelName(MistralAiFimModelName.CODESTRAL_LATEST)
                .stop(List.of("{")) // must stop at the first occurrence of "{"
                .logRequests(true)
                .build();

        String codePrompt = """
                            public static void main
                          """;

        // When
        TestStreamingResponseHandler<String> handler = new TestStreamingResponseHandler<>();
        codestral.generate(codePrompt, handler);

        Response<String> response = handler.get();

        // Then
        assertThat(response.content()).isNotBlank();
        assertThat(response.content()).doesNotContainIgnoringCase(codePrompt);
        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_stream_generate_code_completion_with_suffix_and_max_min_tokens() {

        // Given
        MistralAiStreamingFimModel codestral = MistralAiStreamingFimModel.builder()
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
        TestStreamingResponseHandler<String> handler = new TestStreamingResponseHandler<>();
        codestral.generate(codePrompt, suffix, handler);

        Response<String> response = handler.get();

        // Then
        assertThat(response.content()).isNotBlank();
        assertThat(response.content()).doesNotContainIgnoringCase(codePrompt);
        assertThat(response.content()).doesNotContainIgnoringCase(suffix);
        assertThat(response.finishReason()).isEqualTo(STOP);
    }
}
