package dev.langchain4j.model.openai;

import dev.ai4j.openai4j.chat.ChatCompletionModel;
import dev.langchain4j.model.Tokenizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.model.openai.OpenAiModelName.GPT_3_5_TURBO;
import static dev.langchain4j.model.openai.OpenAiTokenizer.countArguments;
import static org.assertj.core.api.Assertions.assertThat;

class OpenAiTokenizerTest {

    OpenAiTokenizer tokenizer = new OpenAiTokenizer(GPT_3_5_TURBO);

    @Test
    void should_encode_and_decode_text() {
        String originalText = "This is a text which will be encoded and decoded back.";

        List<Integer> tokens = tokenizer.encode(originalText);
        String decodedText = tokenizer.decode(tokens);

        assertThat(decodedText).isEqualTo(originalText);
    }

    @Test
    void should_encode_with_truncation_and_decode_text() {
        String originalText = "This is a text which will be encoded with truncation and decoded back.";

        List<Integer> tokens = tokenizer.encode(originalText, 10);
        assertThat(tokens).hasSize(10);

        String decodedText = tokenizer.decode(tokens);
        assertThat(decodedText).isEqualTo("This is a text which will be encoded with trunc");
    }

    @Test
    void should_count_tokens_in_short_texts() {
        assertThat(tokenizer.estimateTokenCountInText("Hello")).isEqualTo(1);
        assertThat(tokenizer.estimateTokenCountInText("Hello!")).isEqualTo(2);
        assertThat(tokenizer.estimateTokenCountInText("Hello, how are you?")).isEqualTo(6);
    }

    @Test
    void should_count_tokens_in_average_text() {
        String text1 = "Hello, how are you doing? What do you want to talk about?";
        assertThat(tokenizer.estimateTokenCountInText(text1)).isEqualTo(15);

        String text2 = String.join(" ", repeat("Hello, how are you doing? What do you want to talk about?", 2));
        assertThat(tokenizer.estimateTokenCountInText(text2)).isEqualTo(2 * 15);

        String text3 = String.join(" ", repeat("Hello, how are you doing? What do you want to talk about?", 3));
        assertThat(tokenizer.estimateTokenCountInText(text3)).isEqualTo(3 * 15);
    }

    @Test
    void should_count_tokens_in_large_text() {
        String text1 = String.join(" ", repeat("Hello, how are you doing? What do you want to talk about?", 10));
        assertThat(tokenizer.estimateTokenCountInText(text1)).isEqualTo(10 * 15);

        String text2 = String.join(" ", repeat("Hello, how are you doing? What do you want to talk about?", 50));
        assertThat(tokenizer.estimateTokenCountInText(text2)).isEqualTo(50 * 15);

        String text3 = String.join(" ", repeat("Hello, how are you doing? What do you want to talk about?", 100));
        assertThat(tokenizer.estimateTokenCountInText(text3)).isEqualTo(100 * 15);
    }

    @Test
    void should_count_arguments() {
        assertThat(countArguments(null)).isEqualTo(0);
        assertThat(countArguments("")).isEqualTo(0);
        assertThat(countArguments(" ")).isEqualTo(0);
        assertThat(countArguments("{}")).isEqualTo(0);
        assertThat(countArguments("{ }")).isEqualTo(0);

        assertThat(countArguments("{\"one\":1}")).isEqualTo(1);
        assertThat(countArguments("{\"one\": 1}")).isEqualTo(1);
        assertThat(countArguments("{\"one\" : 1}")).isEqualTo(1);

        assertThat(countArguments("{\"one\":1,\"two\":2}")).isEqualTo(2);
        assertThat(countArguments("{\"one\": 1,\"two\": 2}")).isEqualTo(2);
        assertThat(countArguments("{\"one\" : 1,\"two\" : 2}")).isEqualTo(2);

        assertThat(countArguments("{\"one\":1,\"two\":2,\"three\":3}")).isEqualTo(3);
        assertThat(countArguments("{\"one\": 1,\"two\": 2,\"three\": 3}")).isEqualTo(3);
        assertThat(countArguments("{\"one\" : 1,\"two\" : 2,\"three\" : 3}")).isEqualTo(3);
    }

    @ParameterizedTest
    @EnumSource(ChatCompletionModel.class)
    void should_support_all_models(ChatCompletionModel model) {

        // given
        Tokenizer tokenizer = new OpenAiTokenizer(model.toString());

        // when
        int tokenCount = tokenizer.estimateTokenCountInText("a");

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