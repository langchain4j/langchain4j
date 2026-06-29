package dev.langchain4j.model.ollama;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OllamaStreamingErrorIT {

    private MockWebServer mockWebServer;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.close();
    }

    @Test
    void should_propagate_streaming_error_to_handler_onError() throws Exception {

        // given
        // Ollama returns error JSON during streaming
        // See https://docs.ollama.com/api/errors#errors-that-occur-while-streaming
        String errorJson = "{\"error\":\"an error was encountered while running the model\"}\n";

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(errorJson)
                .setHeader("Content-Type", "application/x-ndjson"));

        OllamaStreamingChatModel model = OllamaStreamingChatModel.builder()
                .baseUrl("http://localhost:" + mockWebServer.getPort())
                .modelName("test-model")
                .timeout(Duration.ofSeconds(5))
                .build();

        CompletableFuture<Throwable> futureError = new CompletableFuture<>();

        // when
        model.chat("test prompt", new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
                futureError.completeExceptionally(
                        new AssertionError("onPartialResponse() should not be called for error responses"));
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                futureError.completeExceptionally(
                        new AssertionError("onCompleteResponse() should not be called for error responses"));
            }

            @Override
            public void onError(Throwable error) {
                futureError.complete(error);
            }
        });

        // then
        Throwable error = futureError.get(5, SECONDS);
        assertThat(error).isInstanceOf(OllamaStreamingException.class);
        assertThat(error.getMessage()).contains("an error was encountered while running the model");
    }

    @Test
    void should_propagate_streaming_error_after_partial_response() throws Exception {

        // given
        // Ollama streams a partial response, then returns an error
        String partialThenError =
                "{\"model\":\"test\",\"message\":{\"role\":\"assistant\",\"content\":\"Hello\"},\"done\":false}\n"
                        + "{\"error\":\"invalid character in string literal\"}\n";

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(partialThenError)
                .setHeader("Content-Type", "application/x-ndjson"));

        OllamaStreamingChatModel model = OllamaStreamingChatModel.builder()
                .baseUrl("http://localhost:" + mockWebServer.getPort())
                .modelName("test-model")
                .timeout(Duration.ofSeconds(5))
                .build();

        CompletableFuture<Throwable> futureError = new CompletableFuture<>();
        StringBuilder partialResponses = new StringBuilder();

        // when
        model.chat("test prompt", new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
                partialResponses.append(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                futureError.completeExceptionally(
                        new AssertionError("onCompleteResponse() should not be called when streaming ends with error"));
            }

            @Override
            public void onError(Throwable error) {
                futureError.complete(error);
            }
        });

        // then
        Throwable error = futureError.get(5, SECONDS);
        assertThat(error).isInstanceOf(OllamaStreamingException.class);
        assertThat(error.getMessage()).contains("invalid character in string literal");
        assertThat(partialResponses.toString()).isEqualTo("Hello");
    }
}
