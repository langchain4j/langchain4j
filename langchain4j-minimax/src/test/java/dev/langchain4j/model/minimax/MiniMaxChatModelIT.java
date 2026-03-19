package dev.langchain4j.model.minimax;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Integration tests for {@link MiniMaxChatModel}.
 * These tests require a valid MINIMAX_API_KEY environment variable.
 */
@EnabledIfEnvironmentVariable(named = "MINIMAX_API_KEY", matches = ".+")
class MiniMaxChatModelIT {

    @Test
    void should_generate_response() {
        MiniMaxChatModel model = MiniMaxChatModel.builder()
                .apiKey(System.getenv("MINIMAX_API_KEY"))
                .modelName(MiniMaxChatModelName.MINIMAX_M2_7)
                .temperature(0.1)
                .maxTokens(100)
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("What is 2+2? Answer with just the number."))
                .build();

        ChatResponse response = model.chat(request);

        assertThat(response).isNotNull();
        assertThat(response.aiMessage()).isNotNull();
        assertThat(response.aiMessage().text()).contains("4");
    }

    @Test
    void should_generate_response_with_highspeed_model() {
        MiniMaxChatModel model = MiniMaxChatModel.builder()
                .apiKey(System.getenv("MINIMAX_API_KEY"))
                .modelName(MiniMaxChatModelName.MINIMAX_M2_7_HIGHSPEED)
                .temperature(0.1)
                .maxTokens(100)
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("What is 3+3? Answer with just the number."))
                .build();

        ChatResponse response = model.chat(request);

        assertThat(response).isNotNull();
        assertThat(response.aiMessage()).isNotNull();
        assertThat(response.aiMessage().text()).contains("6");
    }

    @Test
    void should_respect_max_tokens() {
        MiniMaxChatModel model = MiniMaxChatModel.builder()
                .apiKey(System.getenv("MINIMAX_API_KEY"))
                .modelName(MiniMaxChatModelName.MINIMAX_M2_7_HIGHSPEED)
                .temperature(0.1)
                .maxTokens(10)
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Write a very long essay about artificial intelligence."))
                .build();

        ChatResponse response = model.chat(request);

        assertThat(response).isNotNull();
        assertThat(response.aiMessage()).isNotNull();
        // With max 10 tokens, the response should be short
        assertThat(response.aiMessage().text().length()).isLessThan(200);
    }
}
