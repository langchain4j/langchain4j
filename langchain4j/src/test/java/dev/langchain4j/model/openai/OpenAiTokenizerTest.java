package dev.langchain4j.model.openai;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ChatMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static dev.langchain4j.data.message.ToolExecutionResultMessage.toolExecutionResultMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.openai.OpenAiModelName.GPT_3_5_TURBO;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

class OpenAiTokenizerTest {

    OpenAiTokenizer tokenizer = new OpenAiTokenizer(GPT_3_5_TURBO);

    @ParameterizedTest
    @MethodSource
    void should_count_tokens_for_messages(List<ChatMessage> messages, int expectedTokenCount) {
        int tokenCount = tokenizer.countTokens(messages);
        assertThat(tokenCount).isEqualTo(expectedTokenCount);
    }

    static Stream<Arguments> should_count_tokens_for_messages() {
        // expected token count was taken from real OpenAI responses (usage.prompt_tokens)
        return Stream.of(
                Arguments.of(singletonList(userMessage("hello")), 8),
                Arguments.of(singletonList(userMessage("Klaus", "hello")), 11),
                Arguments.of(asList(userMessage("hello"), aiMessage("hi there")), 14),
                Arguments.of(asList(
                        userMessage("How much is 2 plus 2?"),
                        aiMessage(ToolExecutionRequest.builder()
                                .name("calculator")
                                .arguments("{\"a\":2, \"b\":2}")
                                .build())
                ), 35),
                Arguments.of(asList(
                        userMessage("How much is 2 plus 2?"),
                        aiMessage(ToolExecutionRequest.builder()
                                .name("calculator")
                                .arguments("{\"a\":2, \"b\":2}")
                                .build()),
                        toolExecutionResultMessage("calculator", "4")
                ), 40)
        );
    }

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
        assertThat(tokenizer.countTokens("Hello")).isEqualTo(1);
        assertThat(tokenizer.countTokens("Hello!")).isEqualTo(2);
        assertThat(tokenizer.countTokens("Hello, how are you?")).isEqualTo(6);
    }

    @Test
    void should_count_tokens_in_average_text() {
        String text1 = "Hello, how are you doing? What do you want to talk about?";
        assertThat(tokenizer.countTokens(text1)).isEqualTo(15);

        String text2 = String.join(" ", repeat("Hello, how are you doing? What do you want to talk about?", 2));
        assertThat(tokenizer.countTokens(text2)).isEqualTo(2 * 15);

        String text3 = String.join(" ", repeat("Hello, how are you doing? What do you want to talk about?", 3));
        assertThat(tokenizer.countTokens(text3)).isEqualTo(3 * 15);
    }

    @Test
    void should_count_tokens_in_large_text() {
        String text1 = String.join(" ", repeat("Hello, how are you doing? What do you want to talk about?", 10));
        assertThat(tokenizer.countTokens(text1)).isEqualTo(10 * 15);

        String text2 = String.join(" ", repeat("Hello, how are you doing? What do you want to talk about?", 50));
        assertThat(tokenizer.countTokens(text2)).isEqualTo(50 * 15);

        String text3 = String.join(" ", repeat("Hello, how are you doing? What do you want to talk about?", 100));
        assertThat(tokenizer.countTokens(text3)).isEqualTo(100 * 15);
    }

    public static List<String> repeat(String s, int n) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            result.add(s);
        }
        return result;
    }
}