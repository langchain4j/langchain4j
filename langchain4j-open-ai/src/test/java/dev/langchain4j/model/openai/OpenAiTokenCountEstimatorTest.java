package dev.langchain4j.model.openai;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.TokenCountEstimator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_3_5_TURBO;
import static org.assertj.core.api.Assertions.assertThat;

class OpenAiTokenCountEstimatorTest {

    OpenAiTokenCountEstimator tokenCountEstimator = new OpenAiTokenCountEstimator(GPT_3_5_TURBO);

    @Test
    void should_encode_and_decode_text() {
        String originalText = "This is a text which will be encoded and decoded back.";

        List<Integer> tokens = tokenCountEstimator.encode(originalText);
        String decodedText = tokenCountEstimator.decode(tokens);

        assertThat(decodedText).isEqualTo(originalText);
    }

    @Test
    void should_encode_with_truncation_and_decode_text() {
        String originalText = "This is a text which will be encoded with truncation and decoded back.";

        List<Integer> tokens = tokenCountEstimator.encode(originalText, 10);
        assertThat(tokens).hasSize(10);

        String decodedText = tokenCountEstimator.decode(tokens);
        assertThat(decodedText).isEqualTo("This is a text which will be encoded with trunc");
    }

    @Test
    void should_count_tokens_in_short_texts() {
        assertThat(tokenCountEstimator.estimateTokenCountInText("Hello")).isEqualTo(1);
        assertThat(tokenCountEstimator.estimateTokenCountInText("Hello!")).isEqualTo(2);
        assertThat(tokenCountEstimator.estimateTokenCountInText("Hello, how are you?")).isEqualTo(6);

        assertThat(tokenCountEstimator.estimateTokenCountInText("")).isZero();
        assertThat(tokenCountEstimator.estimateTokenCountInText("\n")).isEqualTo(1);
        assertThat(tokenCountEstimator.estimateTokenCountInText("\n\n")).isEqualTo(1);
        assertThat(tokenCountEstimator.estimateTokenCountInText("\n \n\n")).isEqualTo(2);
    }

    @Test
    void should_count_tokens_in_ai_message_with_null_tool_argument() {

        // given
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .id("tool-id")
                .name("tool-name")
                .arguments("""
                        {
                            "name": "Klaus",
                            "address": null
                        }
                        """)
                .build();

        AiMessage aiMessage = AiMessage.from(toolExecutionRequest, toolExecutionRequest); // duplicate on purpose

        // when
        int tokenCount = tokenCountEstimator.estimateTokenCountInMessage(aiMessage);

        // then
        assertThat(tokenCount).isPositive();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "{}"})
    void should_count_tokens_in_ai_message_with_empty_tool_arguments(String arguments) {

        // given
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .id("tool-id")
                .name("tool-name")
                .arguments(arguments)
                .build();

        AiMessage aiMessage = AiMessage.from(toolExecutionRequest, toolExecutionRequest); // duplicate on purpose

        // when
        int tokenCount = tokenCountEstimator.estimateTokenCountInMessage(aiMessage);

        // then
        assertThat(tokenCount).isPositive();
    }

    @Test
    void should_count_tokens_in_average_text() {
        String text1 = "Hello, how are you doing? What do you want to talk about?";
        assertThat(tokenCountEstimator.estimateTokenCountInText(text1)).isEqualTo(15);

        String text2 = String.join(" ", repeat("Hello, how are you doing? What do you want to talk about?", 2));
        assertThat(tokenCountEstimator.estimateTokenCountInText(text2)).isEqualTo(2 * 15);

        String text3 = String.join(" ", repeat("Hello, how are you doing? What do you want to talk about?", 3));
        assertThat(tokenCountEstimator.estimateTokenCountInText(text3)).isEqualTo(3 * 15);
    }

    @Test
    void should_count_tokens_in_large_text() {
        String text1 = String.join(" ", repeat("Hello, how are you doing? What do you want to talk about?", 10));
        assertThat(tokenCountEstimator.estimateTokenCountInText(text1)).isEqualTo(10 * 15);

        String text2 = String.join(" ", repeat("Hello, how are you doing? What do you want to talk about?", 50));
        assertThat(tokenCountEstimator.estimateTokenCountInText(text2)).isEqualTo(50 * 15);

        String text3 = String.join(" ", repeat("Hello, how are you doing? What do you want to talk about?", 100));
        assertThat(tokenCountEstimator.estimateTokenCountInText(text3)).isEqualTo(100 * 15);
    }

    @ParameterizedTest
    @EnumSource(OpenAiChatModelName.class)
    void should_support_all_chat_model_names(OpenAiChatModelName modelName) {

        // given
        TokenCountEstimator tokenCountEstimator = new OpenAiTokenCountEstimator(modelName);

        // when
        int tokenCount = tokenCountEstimator.estimateTokenCountInText("a");

        // then
        assertThat(tokenCount).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(OpenAiEmbeddingModelName.class)
    void should_support_all_embedding_model_names(OpenAiEmbeddingModelName modelName) {

        // given
        TokenCountEstimator tokenCountEstimator = new OpenAiTokenCountEstimator(modelName);

        // when
        int tokenCount = tokenCountEstimator.estimateTokenCountInText("a");

        // then
        assertThat(tokenCount).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(OpenAiLanguageModelName.class)
    void should_support_all_language_model_names(OpenAiLanguageModelName modelName) {

        // given
        TokenCountEstimator tokenCountEstimator = new OpenAiTokenCountEstimator(modelName);

        // when
        int tokenCount = tokenCountEstimator.estimateTokenCountInText("a");

        // then
        assertThat(tokenCount).isEqualTo(1);
    }

    static List<String> repeat(String strings, int n) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            result.add(strings);
        }
        return result;
    }
}
