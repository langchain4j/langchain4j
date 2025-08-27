package dev.langchain4j.model.anthropic;

import static dev.langchain4j.model.anthropic.AnthropicChatModelName.CLAUDE_3_5_HAIKU_20241022;
import static dev.langchain4j.model.anthropic.AnthropicChatModelName.CLAUDE_3_OPUS_20240229;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.TokenCountEstimator;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class AnthropicTokenCountEstimatorIT {

    private static final String ANTHROPIC_API_KEY = System.getenv("ANTHROPIC_API_KEY");

    @Test
    void should_estimate_token_count_in_text() {
        // given
        TokenCountEstimator tokenCountEstimator = AnthropicTokenCountEstimator.builder()
                .modelName(CLAUDE_3_5_HAIKU_20241022)
                .apiKey(ANTHROPIC_API_KEY)
                .logRequests(true)
                .logResponses(true)
                .build();

        String text = "Hello, world! This is a test message for token counting.";

        // when
        int count = tokenCountEstimator.estimateTokenCountInText(text);

        // then
        assertThat(count).isGreaterThan(0);
    }

    @Test
    void should_estimate_token_count_in_message() {
        // given
        TokenCountEstimator tokenCountEstimator = AnthropicTokenCountEstimator.builder()
                .modelName(CLAUDE_3_OPUS_20240229)
                .apiKey(ANTHROPIC_API_KEY)
                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage message = UserMessage.from("What's the population of Tokyo?");

        // when
        int count = tokenCountEstimator.estimateTokenCountInMessage(message);

        // then
        assertThat(count).isGreaterThan(0);
    }

    @Test
    void should_estimate_token_count_in_messages() {
        // given
        TokenCountEstimator tokenCountEstimator = AnthropicTokenCountEstimator.builder()
                .modelName(CLAUDE_3_5_HAIKU_20241022)
                .apiKey(ANTHROPIC_API_KEY)
                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage1 = UserMessage.from("What's the tallest mountain in the world?");
        AiMessage aiMessage = AiMessage.from("The tallest mountain in the world is Mount Everest, with a height of 8,848.86 meters (29,031.7 feet) above sea level.");
        UserMessage userMessage2 = UserMessage.from("And what's the second tallest?");

        // when
        int count = tokenCountEstimator.estimateTokenCountInMessages(Arrays.asList(userMessage1, aiMessage, userMessage2));

        // then
        assertThat(count).isGreaterThan(0);
    }

    @Test
    void should_estimate_token_count_in_messages_with_system_prompt() {
        // given
        TokenCountEstimator tokenCountEstimator = AnthropicTokenCountEstimator.builder()
                .modelName(CLAUDE_3_5_HAIKU_20241022)
                .apiKey(ANTHROPIC_API_KEY)
                .logRequests(true)
                .logResponses(true)
                .build();

        SystemMessage systemMessage = SystemMessage.from("You are a helpful assistant.");
        UserMessage userMessage1 = UserMessage.from("What is the largest planet in our solar system?");
        AiMessage aiMessage = AiMessage.from("The largest planet in our solar system is Jupiter.");
        UserMessage userMessage2 = UserMessage.from("How many moons does Jupiter have?");

        // when
        int count = tokenCountEstimator.estimateTokenCountInMessages(Arrays.asList(systemMessage, userMessage1, aiMessage, userMessage2));

        // then
        assertThat(count).isGreaterThan(0);
    }
}
