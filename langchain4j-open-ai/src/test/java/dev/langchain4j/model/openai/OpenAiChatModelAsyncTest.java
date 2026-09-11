package dev.langchain4j.model.openai;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Deterministic (WireMock, no API key) coverage of the non-blocking
 * {@link OpenAiChatModel#chatAsync(ChatRequest)}:
 * <ul>
 *     <li>an HTTP error must complete the future exceptionally (errors delivered via the async channel,
 *         not thrown); and</li>
 *     <li>cancelling the returned future aborts the in-flight request end-to-end (propagated down to the
 *         HTTP client) and resolves immediately, without waiting for the response.</li>
 * </ul>
 * The happy path is exercised against the real API by {@code AbstractChatModelIT#should_chat_asynchronously}.
 */
class OpenAiChatModelAsyncTest {

    private static final String PATH = "/v1/chat/completions";

    private WireMockServer wireMockServer;

    @BeforeEach
    void startServer() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
    }

    @AfterEach
    void stopServer() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test
    void chatAsync_completes_exceptionally_on_http_error() {
        wireMockServer.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse()
                .withStatus(500)
                .withBody("{\"error\":{\"message\":\"boom\"}}")));

        CompletableFuture<ChatResponse> future = model()
                .chatAsync(ChatRequest.builder().messages(UserMessage.from("hi")).build());

        // The failure is delivered through the future (not thrown synchronously).
        assertThatThrownBy(() -> future.get(10, SECONDS)).isInstanceOf(ExecutionException.class);
    }

    @Test
    void chatAsync_can_be_cancelled_while_in_flight() {
        // the server holds the response for a long time, keeping the request in flight
        wireMockServer.stubFor(post(urlEqualTo(PATH))
                .willReturn(aResponse().withStatus(200).withFixedDelay(30_000).withBody("{}")));

        CompletableFuture<ChatResponse> future = model()
                .chatAsync(ChatRequest.builder().messages(UserMessage.from("hi")).build());

        assertThat(future).isNotDone();

        boolean cancelled = future.cancel(true);

        // cancellation propagates through all the layers (chatAsync -> doChatAsync -> HTTP client) and the
        // future resolves immediately as cancelled, without waiting for the 30s server delay
        assertThat(cancelled).isTrue();
        assertThat(future).isCancelled();
        assertThatThrownBy(() -> future.get(5, SECONDS)).isInstanceOf(CancellationException.class);
    }

    private OpenAiChatModel model() {
        return OpenAiChatModel.builder()
                .baseUrl("http://localhost:" + wireMockServer.port() + "/v1")
                .apiKey("test-key")
                .modelName("gpt-4o-mini")
                .maxRetries(0)
                .build();
    }
}
