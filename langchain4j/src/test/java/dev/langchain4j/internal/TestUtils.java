package dev.langchain4j.internal;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static dev.langchain4j.data.message.SystemMessage.systemMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_3_5_TURBO;
import static org.assertj.core.api.Assertions.assertThat;

public class TestUtils {

    private static final int EXTRA_TOKENS_PER_EACH_MESSAGE =
            3 /* extra tokens for each message */ + 1 /* extra token for 'role' */;
    private static final OpenAiTokenCountEstimator TOKEN_COUNT_ESTIMATOR = new OpenAiTokenCountEstimator(GPT_3_5_TURBO);

    @ParameterizedTest
    @ValueSource(ints = {5, 10, 25, 50, 100, 250, 500, 1000})
    void should_create_system_message_with_tokens(int numberOfTokens) {
        SystemMessage systemMessage = systemMessageWithTokens(numberOfTokens);

        assertThat(TOKEN_COUNT_ESTIMATOR.estimateTokenCountInMessage(systemMessage)).isEqualTo(numberOfTokens);
    }

    public static SystemMessage systemMessageWithTokens(int numberOfTokens) {
        return systemMessage(textWithTokens(numberOfTokens - EXTRA_TOKENS_PER_EACH_MESSAGE));
    }

    @ParameterizedTest
    @ValueSource(ints = {5, 10, 25, 50, 100, 250, 500, 1000})
    void should_create_user_message_with_tokens(int numberOfTokens) {
        UserMessage userMessage = userMessageWithTokens(numberOfTokens);

        assertThat(TOKEN_COUNT_ESTIMATOR.estimateTokenCountInMessage(userMessage)).isEqualTo(numberOfTokens);
    }

    public static UserMessage userMessageWithTokens(int numberOfTokens) {
        return userMessage(textWithTokens(numberOfTokens - EXTRA_TOKENS_PER_EACH_MESSAGE));
    }

    @ParameterizedTest
    @ValueSource(ints = {5, 10, 25, 50, 100, 250, 500, 1000})
    void should_create_ai_message_with_tokens(int numberOfTokens) {
        AiMessage aiMessage = aiMessageWithTokens(numberOfTokens);

        assertThat(TOKEN_COUNT_ESTIMATOR.estimateTokenCountInMessage(aiMessage)).isEqualTo(numberOfTokens);
    }

    public static AiMessage aiMessageWithTokens(int numberOfTokens) {
        return aiMessage(textWithTokens(numberOfTokens - EXTRA_TOKENS_PER_EACH_MESSAGE));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 5, 10, 25, 50, 100, 250, 500, 1000})
    void should_generate_tokens(int numberOfTokens) {
        String text = textWithTokens(numberOfTokens);

        assertThat(TOKEN_COUNT_ESTIMATOR.estimateTokenCountInText(text)).isEqualTo(numberOfTokens);
    }

    private static String textWithTokens(int n) {
        String text = String.join(" ", repeat("one two", n));
        return TOKEN_COUNT_ESTIMATOR.decode(TOKEN_COUNT_ESTIMATOR.encode(text, n));
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
        final var result = new ArrayList<String>();
        for (int i = 0; i < n; i++) {
            result.add(s);
        }
        return result;
    }
}
