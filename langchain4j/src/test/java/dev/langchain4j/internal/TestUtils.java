package dev.langchain4j.internal;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static dev.langchain4j.data.message.SystemMessage.systemMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.openai.OpenAiModelName.GPT_4;
import static org.assertj.core.api.Assertions.assertThat;

public class TestUtils {

    private static final int EXTRA_TOKENS_PER_EACH_GPT_4_MESSAGE =
            3 /* extra tokens for each message */ + 1 /* extra token for 'role' */;
    private static final OpenAiTokenizer TOKENIZER = new OpenAiTokenizer(GPT_4);

    @ParameterizedTest
    @ValueSource(ints = {5, 10, 25, 50, 100, 250, 500, 1000})
    void should_create_system_message_with_tokens(int numberOfTokens) {
        SystemMessage systemMessage = systemMessageWith(numberOfTokens);

        assertThat(TOKENIZER.countTokens(systemMessage)).isEqualTo(numberOfTokens);
    }

    public static SystemMessage systemMessageWith(int numberOfTokens) {
        return systemMessage(generateTextWith(numberOfTokens - EXTRA_TOKENS_PER_EACH_GPT_4_MESSAGE));
    }

    @ParameterizedTest
    @ValueSource(ints = {5, 10, 25, 50, 100, 250, 500, 1000})
    void should_create_user_message_with_tokens(int numberOfTokens) {
        UserMessage userMessage = userMessageWith(numberOfTokens);

        assertThat(TOKENIZER.countTokens(userMessage)).isEqualTo(numberOfTokens);
    }

    public static UserMessage userMessageWith(int numberOfTokens) {
        return userMessage(generateTextWith(numberOfTokens - EXTRA_TOKENS_PER_EACH_GPT_4_MESSAGE));
    }

    @ParameterizedTest
    @ValueSource(ints = {5, 10, 25, 50, 100, 250, 500, 1000})
    void should_create_ai_message_with_tokens(int numberOfTokens) {
        AiMessage aiMessage = aiMessageWith(numberOfTokens);

        assertThat(TOKENIZER.countTokens(aiMessage)).isEqualTo(numberOfTokens);
    }

    public static AiMessage aiMessageWith(int numberOfTokens) {
        return aiMessage(generateTextWith(numberOfTokens - EXTRA_TOKENS_PER_EACH_GPT_4_MESSAGE));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 5, 10, 25, 50, 100, 250, 500, 1000})
    void should_generate_tokens(int numberOfTokens) {
        String text = generateTextWith(numberOfTokens);

        assertThat(TOKENIZER.countTokens(text)).isEqualTo(numberOfTokens);
    }

    private static String generateTextWith(int n) {
        String text = String.join(" ", repeat("one two", n));
        return TOKENIZER.decode(TOKENIZER.encode(text, n));
    }

    @Test
    void should_repeat_n_times() {
        assertThat(repeat("word", 1))
                .hasSize(1)
                .containsExactly("word");

        assertThat(repeat("word", 2))
                .hasSize(2)
                .containsExactly("word", "word");

        assertThat(repeat("word", 3))
                .hasSize(3)
                .containsExactly("word", "word", "word");
    }

    public static List<String> repeat(String s, int n) {
        val result = new ArrayList<String>();
        for (int i = 0; i < n; i++) {
            result.add(s);
        }
        return result;
    }
}
