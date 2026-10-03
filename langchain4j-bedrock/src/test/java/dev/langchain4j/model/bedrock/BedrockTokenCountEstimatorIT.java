package dev.langchain4j.model.bedrock;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.TokenCountEstimator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
class BedrockTokenCountEstimatorIT {

    private final TokenCountEstimator estimator = BedrockTokenCountEstimator.builder()
            .modelId("anthropic.claude-haiku-4-5-20251001-v1:0")
            .logRequests(true)
            .logResponses(true)
            .build();

    @Test
    void should_estimate_token_count_in_text() {
        // when
        int count = estimator.estimateTokenCountInText("hi, how are you doing?");

        // then
        assertThat(count).isGreaterThan(0);
    }

    @Test
    void should_estimate_token_count_in_message() {
        // when
        int count = estimator.estimateTokenCountInMessage(UserMessage.from("What is the capital of Germany?"));

        // then
        assertThat(count).isGreaterThan(0);
    }

    @Test
    void should_estimate_token_count_in_messages() {
        // when
        int count = estimator.estimateTokenCountInMessages(List.of(
                UserMessage.from("What is the capital of Germany?"),
                AiMessage.from("The capital of Germany is Berlin."),
                UserMessage.from("And what is the capital of France?")));

        // then
        assertThat(count).isGreaterThan(0);
    }

    @Test
    void should_estimate_token_count_in_messages_with_system_prompt() {
        // when
        int count = estimator.estimateTokenCountInMessages(List.of(
                SystemMessage.from("You are a helpful assistant that provides concise answers."),
                UserMessage.from("What is the capital of Germany?"),
                AiMessage.from("The capital of Germany is Berlin."),
                UserMessage.from("And what is the capital of France?")));

        // then
        assertThat(count).isGreaterThan(0);
    }
}
