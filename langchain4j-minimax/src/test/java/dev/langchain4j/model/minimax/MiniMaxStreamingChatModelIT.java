package dev.langchain4j.model.minimax;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Integration tests for {@link MiniMaxStreamingChatModel}.
 * These tests require a valid MINIMAX_API_KEY environment variable.
 */
@EnabledIfEnvironmentVariable(named = "MINIMAX_API_KEY", matches = ".+")
class MiniMaxStreamingChatModelIT {

    @Test
    void should_stream_response() throws Exception {
        MiniMaxStreamingChatModel model = MiniMaxStreamingChatModel.builder()
                .apiKey(System.getenv("MINIMAX_API_KEY"))
                .modelName(MiniMaxChatModelName.MINIMAX_M2_7)
                .temperature(0.1)
                .maxTokens(100)
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("What is 2+2? Answer with just the number."))
                .build();

        CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        StringBuilder contentBuilder = new StringBuilder();

        model.chat(request, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                contentBuilder.append(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                future.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                future.completeExceptionally(error);
            }
        });

        ChatResponse response = future.get(30, TimeUnit.SECONDS);

        assertThat(response).isNotNull();
        assertThat(response.aiMessage()).isNotNull();
        assertThat(response.aiMessage().text()).contains("4");
        assertThat(contentBuilder.toString()).contains("4");
    }

    @Test
    void should_stream_response_with_highspeed_model() throws Exception {
        MiniMaxStreamingChatModel model = MiniMaxStreamingChatModel.builder()
                .apiKey(System.getenv("MINIMAX_API_KEY"))
                .modelName(MiniMaxChatModelName.MINIMAX_M2_7_HIGHSPEED)
                .temperature(0.1)
                .maxTokens(100)
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("What is 3+3? Answer with just the number."))
                .build();

        CompletableFuture<ChatResponse> future = new CompletableFuture<>();

        model.chat(request, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                // Streaming content received
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                future.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                future.completeExceptionally(error);
            }
        });

        ChatResponse response = future.get(30, TimeUnit.SECONDS);

        assertThat(response).isNotNull();
        assertThat(response.aiMessage()).isNotNull();
        assertThat(response.aiMessage().text()).contains("6");
    }
}
