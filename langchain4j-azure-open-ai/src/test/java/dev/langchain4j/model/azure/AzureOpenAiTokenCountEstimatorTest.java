package dev.langchain4j.model.azure;

import dev.langchain4j.model.TokenCountEstimator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.model.azure.AzureOpenAiChatModelName.GPT_3_5_TURBO;
import static org.assertj.core.api.Assertions.assertThat;

class AzureOpenAiTokenCountEstimatorTest {

    TokenCountEstimator tokenCountEstimator = new AzureOpenAiTokenCountEstimator(GPT_3_5_TURBO.modelType());

    @Test
    void should_count_tokens_in_short_texts() {
        assertThat(tokenCountEstimator.estimateTokenCountInText("Hello")).isEqualTo(1);
        assertThat(tokenCountEstimator.estimateTokenCountInText("Hello!")).isEqualTo(2);
        assertThat(tokenCountEstimator.estimateTokenCountInText("Hello, how are you?")).isEqualTo(6);

        assertThat(tokenCountEstimator.estimateTokenCountInText("")).isEqualTo(0);
        assertThat(tokenCountEstimator.estimateTokenCountInText("\n")).isEqualTo(1);
        assertThat(tokenCountEstimator.estimateTokenCountInText("\n\n")).isEqualTo(1);
        assertThat(tokenCountEstimator.estimateTokenCountInText("\n \n\n")).isEqualTo(2);
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
    @EnumSource(AzureOpenAiChatModelName.class)
    void should_support_all_chat_models(AzureOpenAiChatModelName modelName) {

        // given
        TokenCountEstimator tokenCountEstimator = new AzureOpenAiTokenCountEstimator(modelName);

        // when
        int tokenCount = tokenCountEstimator.estimateTokenCountInText("a");

        // then
        assertThat(tokenCount).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(AzureOpenAiEmbeddingModelName.class)
    void should_support_all_embedding_models(AzureOpenAiEmbeddingModelName modelName) {

        // given
        TokenCountEstimator tokenCountEstimator = new AzureOpenAiTokenCountEstimator(modelName);

        // when
        int tokenCount = tokenCountEstimator.estimateTokenCountInText("a");

        // then
        assertThat(tokenCount).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(AzureOpenAiLanguageModelName.class)
    void should_support_all_language_models(AzureOpenAiLanguageModelName modelName) {

        // given
        TokenCountEstimator tokenCountEstimator = new AzureOpenAiTokenCountEstimator(modelName);

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
