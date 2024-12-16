package dev.langchain4j.model.mistralai;

import static dev.langchain4j.model.output.FinishReason.STOP;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.chat.TestStreamingResponseHandler;
import dev.langchain4j.model.language.StreamingLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class MistralAiStreamingCompletionModelIT {

    StreamingLanguageModel codestralStream = MistralAiStreamingCompletionModel.builder()
            .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
            .modelName(MistralAiCodeModelName.CODESTRAL_LATEST)
            .logRequests(true)
            .logResponses(true)
            .build();

    @Test
    void should_stream_code_completion_and_return_token_usage_and_finish_reason_length() {
        // Given
        String codePrompt = "public static void main(String[] args) {";

        // When
        TestStreamingResponseHandler<String> handler = new TestStreamingResponseHandler<>();
        codestralStream.generate(codePrompt, handler);

        Response<String> response = handler.get();

        // Then
        System.out.println(String.format("%s%s", codePrompt, response.content())); // print code completion

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
        MistralAiStreamingCompletionModel codestral = MistralAiStreamingCompletionModel.builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .modelName(MistralAiCodeModelName.CODESTRAL_LATEST)
                .logRequests(true)
                .build();

        String codePrompt = "public class HelloWorld {\n" + "\tpublic static void main(String[] args) {\n"
                + "\t\tChatLanguageModel model = MistralAiChatModel.withApiKey(ApiKeys.MISTRALAI_API_KEY);";
        String suffix = "\t\tSystem.out.println(response);\n" + "\t}\n" + "}";

        // When
        TestStreamingResponseHandler<String> handler = new TestStreamingResponseHandler<>();
        codestral.generate(codePrompt, suffix, handler);

        Response<String> response = handler.get();

        // Then
        System.out.println(String.format("%s%s%s", codePrompt, response.content(), suffix)); // print code completion

        assertThat(response.content()).doesNotContainIgnoringCase(codePrompt);
        assertThat(response.content()).doesNotContainIgnoringCase(suffix);
        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_stream_generate_code_completion_with_suffix_and_stops() {
        // Given
        MistralAiStreamingCompletionModel codestral = MistralAiStreamingCompletionModel.builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .modelName(MistralAiCodeModelName.CODESTRAL_LATEST)
                .stops(Arrays.asList("\n\n"))
                .logRequests(true)
                .build();

        String codePrompt = "def is_odd(n): \n return n % 2 == 1 \n def test_is_odd():";
        String suffix = "test_is_odd()";

        // When
        TestStreamingResponseHandler<String> handler = new TestStreamingResponseHandler<>();
        codestral.generate(codePrompt, suffix, handler);

        Response<String> response = handler.get();

        // Then
        System.out.println(String.format("%s%s", codePrompt, response.content())); // print code completion

        assertThat(response.content()).doesNotContainIgnoringCase(codePrompt);
        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_stream_generate_code_completion_with_suffix_and_max_min_tokens() {
        // Given
        MistralAiStreamingCompletionModel codestral = MistralAiStreamingCompletionModel.builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .modelName(MistralAiCodeModelName.CODESTRAL_LATEST)
                .maxTokens(1024)
                .minTokens(0)
                .logRequests(true)
                .build();

        String codePrompt = "public class HelloWorld {\n" + "\tpublic static void main(String[] args) {\n"
                + "\t\tChatLanguageModel model = MistralAiChatModel.withApiKey(ApiKeys.MISTRALAI_API_KEY);";
        String suffix = "\t\tSystem.out.println(response);\n" + "\t}\n" + "}";

        // When
        TestStreamingResponseHandler<String> handler = new TestStreamingResponseHandler<>();
        codestral.generate(codePrompt, suffix, handler);

        Response<String> response = handler.get();

        // Then
        System.out.println(String.format("%s%s%s", codePrompt, response.content(), suffix)); // print code completion

        assertThat(response.content()).doesNotContainIgnoringCase(codePrompt);
        assertThat(response.content()).doesNotContainIgnoringCase(suffix);
        assertThat(response.finishReason()).isEqualTo(STOP);
    }
}
