package dev.langchain4j.model.dashscope;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.Tokenizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.dashscope.QwenTestHelper.apiKey;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
public class QwenTokenizerIT {
    private Tokenizer tokenizer;

    @BeforeEach
    public void setUp() {
        tokenizer = new QwenTokenizer(apiKey(), QwenModelName.QWEN_PLUS);
    }

    @ParameterizedTest
    @MethodSource
    void should_count_tokens_in_messages(List<ChatMessage> messages, int expectedTokenCount) {
        int tokenCount = tokenizer.estimateTokenCountInMessages(messages);
        assertThat(tokenCount).isEqualTo(expectedTokenCount);
    }

    static Stream<Arguments> should_count_tokens_in_messages() {
        return Stream.of(
                Arguments.of(singletonList(userMessage("hello")), 1),
                Arguments.of(singletonList(userMessage("Klaus", "hello")), 1),
                Arguments.of(asList(
                        userMessage("hello"),
                        aiMessage("hi there"),
                        userMessage("bye")
                ), 4)
        );
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

    public static List<String> repeat(String s, int n) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            result.add(s);
        }
        return result;
    }
}
