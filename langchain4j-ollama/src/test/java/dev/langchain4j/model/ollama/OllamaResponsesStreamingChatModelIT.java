package dev.langchain4j.model.ollama;

import static dev.langchain4j.model.ollama.OllamaImage.TINY_DOLPHIN_MODEL;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class OllamaResponsesStreamingChatModelIT extends AbstractOllamaLanguageModelInfrastructure {

    static final String MODEL_NAME = TINY_DOLPHIN_MODEL;

    @Test
    void should_stream_response() throws Exception {
        // given
        OllamaResponsesStreamingChatModel model = OllamaResponsesStreamingChatModel.builder()
                .baseUrl(ollamaBaseUrl(ollama))
                .modelName(MODEL_NAME)
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build();

        CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        StringBuilder contentBuilder = new StringBuilder();

        // when
        model.chat(
                "What is the capital of Germany?",
                new StreamingChatResponseHandler() {
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

        // then
        ChatResponse response = future.get(30, TimeUnit.SECONDS);
        assertThat(contentBuilder.toString()).containsIgnoringCase("Berlin");
        assertThat(response.aiMessage().text()).containsIgnoringCase("Berlin");
        assertThat(response.metadata().modelName()).isEqualTo(MODEL_NAME);
    }

    @Test
    void should_stream_with_instructions() throws Exception {
        // given
        OllamaResponsesStreamingChatModel model = OllamaResponsesStreamingChatModel.builder()
                .baseUrl(ollamaBaseUrl(ollama))
                .modelName(MODEL_NAME)
                .instructions("You are a helpful assistant that always responds in French.")
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build();

        CompletableFuture<ChatResponse> future = new CompletableFuture<>();

        // when
        model.chat(
                "What is your name?",
                new StreamingChatResponseHandler() {
                    @Override
                    public void onPartialResponse(String partialResponse) {}

                    @Override
                    public void onCompleteResponse(ChatResponse completeResponse) {
                        future.complete(completeResponse);
                    }

                    @Override
                    public void onError(Throwable error) {
                        future.completeExceptionally(error);
                    }
                });

        // then
        ChatResponse response = future.get(30, TimeUnit.SECONDS);
        assertThat(response.aiMessage().text()).isNotBlank();
    }
}
