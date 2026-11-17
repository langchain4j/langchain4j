package dev.langchain4j.model.google.genai;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.Duration.ofSeconds;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class GoogleGenAiTimeoutTest {

    // language=json
    private static final String ERROR_RESPONSE =
            """
            {"error": {"code": 400, "message": "test error", "status": "INVALID_ARGUMENT"}}
            """;

    // language=json
    private static final String PARTIAL_RESPONSE =
            """
            {"candidates": [{"content": {"role": "model", "parts": [{"text": "Hel"}]}}]}
            """;

    private HttpServer server;
    private ExecutorService serverExecutor;
    private String baseUrl;
    private final AtomicReference<String> serverTimeoutHeader = new AtomicReference<>();
    private final CountDownLatch releaseStalledResponses = new CountDownLatch(1);

    @BeforeEach
    void startServer() throws IOException {
        // Bind to the loopback address rather than the wildcard: "localhost" can resolve to both ::1 and 127.0.0.1,
        // and the client may then dial an address the server is not reachable on.
        InetAddress loopback = InetAddress.getLoopbackAddress();
        server = HttpServer.create(new InetSocketAddress(loopback, 0), 0);
        serverExecutor = Executors.newCachedThreadPool();
        server.setExecutor(serverExecutor);
        server.start();
        baseUrl = "http://" + loopback.getHostAddress() + ":" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        releaseStalledResponses.countDown();
        server.stop(0);
        serverExecutor.shutdownNow();
    }

    @Test
    void should_limit_each_call_to_20_minutes_by_default() {
        respondWithError();
        ChatModel model = GoogleGenAiChatModel.builder()
                .apiKey("test-key")
                .apiEndpoint(baseUrl)
                .modelName("gemini-test")
                .maxRetries(0)
                .build();

        assertThatThrownBy(() -> model.chat("Hi")).hasMessageContaining("test error");

        // the Google GenAI SDK sends the configured timeout (in seconds) to the server
        assertThat(serverTimeoutHeader.get()).isEqualTo("1200");
    }

    @Test
    void should_limit_each_streaming_call_to_20_minutes_by_default() throws Exception {
        respondWithError();
        StreamingChatModel model = GoogleGenAiStreamingChatModel.builder()
                .apiKey("test-key")
                .apiEndpoint(baseUrl)
                .modelName("gemini-test")
                .build();
        StreamingHandler handler = new StreamingHandler();

        model.chat("Hi", handler);

        assertThat(handler.error.get(30, SECONDS)).hasMessageContaining("test error");
        assertThat(serverTimeoutHeader.get()).isEqualTo("1200");
    }

    @ParameterizedTest
    @MethodSource("requestsOfOtherClasses")
    void should_limit_each_request_of_other_classes_to_20_minutes_by_default(
            Function<String, ThrowingCallable> request) {
        respondWithError();

        catchThrowable(request.apply(baseUrl));

        assertThat(serverTimeoutHeader.get()).isEqualTo("1200");
    }

    static Stream<Named<Function<String, ThrowingCallable>>> requestsOfOtherClasses() {
        return Stream.of(
                Named.of("embedding model", url -> () -> GoogleGenAiEmbeddingModel.builder()
                        .apiKey("test-key")
                        .apiEndpoint(url)
                        .modelName("gemini-test")
                        .maxRetries(0)
                        .build()
                        .embed("Hi")),
                Named.of("image model", url -> () -> GoogleGenAiImageModel.builder()
                        .apiKey("test-key")
                        .apiEndpoint(url)
                        .modelName("gemini-test")
                        .maxRetries(0)
                        .build()
                        .generate("Hi")),
                Named.of("text-to-speech model", url -> () -> GoogleGenAiTextToSpeechModel.builder()
                        .apiKey("test-key")
                        .apiEndpoint(url)
                        .modelName("gemini-test")
                        .maxRetries(0)
                        .build()
                        .synthesize("Hi")),
                Named.of("token count estimator", url -> () -> GoogleGenAiTokenCountEstimator.builder()
                        .apiKey("test-key")
                        .apiEndpoint(url)
                        .modelName("gemini-test")
                        .maxRetries(0)
                        .build()
                        .estimateTokenCountInText("Hi")),
                Named.of("model catalog", url -> () -> GoogleGenAiModelCatalog.builder()
                        .apiKey("test-key")
                        .apiEndpoint(url)
                        .build()
                        .listModels()),
                Named.of("batch chat model", url -> () -> GoogleGenAiBatchChatModel.builder()
                        .apiKey("test-key")
                        .apiEndpoint(url)
                        .modelName("gemini-test")
                        .maxRetries(0)
                        .build()
                        .retrieve("batches/test")),
                Named.of("batch embedding model", url -> () -> GoogleGenAiBatchEmbeddingModel.builder()
                        .apiKey("test-key")
                        .apiEndpoint(url)
                        .modelName("gemini-test")
                        .maxRetries(0)
                        .build()
                        .retrieve("batches/test")),
                Named.of("batch image model", url -> () -> GoogleGenAiBatchImageModel.builder()
                        .apiKey("test-key")
                        .apiEndpoint(url)
                        .modelName("gemini-test")
                        .maxRetries(0)
                        .build()
                        .retrieve("batches/test")));
    }

    @Test
    void should_use_configured_timeout() {
        respondWithError();
        ChatModel model = GoogleGenAiChatModel.builder()
                .apiKey("test-key")
                .apiEndpoint(baseUrl)
                .modelName("gemini-test")
                .timeout(ofSeconds(30))
                .maxRetries(0)
                .build();

        assertThatThrownBy(() -> model.chat("Hi")).hasMessageContaining("test error");

        assertThat(serverTimeoutHeader.get()).isEqualTo("30");
    }

    @Test
    void should_limit_each_files_call_to_20_minutes_by_default() {
        respondWithError();
        GoogleGenAiFiles files =
                GoogleGenAiFiles.builder().apiKey("test-key").apiEndpoint(baseUrl).build();

        assertThatThrownBy(() -> files.getMetadata("files/test")).hasMessageContaining("test error");

        assertThat(serverTimeoutHeader.get()).isEqualTo("1200");
    }

    @Test
    void should_use_configured_timeout_for_files() {
        respondWithError();
        GoogleGenAiFiles files = GoogleGenAiFiles.builder()
                .apiKey("test-key")
                .apiEndpoint(baseUrl)
                .timeout(ofSeconds(30))
                .build();

        assertThatThrownBy(() -> files.getMetadata("files/test")).hasMessageContaining("test error");

        assertThat(serverTimeoutHeader.get()).isEqualTo("30");
    }

    @Test
    void should_limit_each_caches_call_to_20_minutes_by_default() {
        respondWithError();
        GoogleGenAiCaches caches =
                GoogleGenAiCaches.builder().apiKey("test-key").apiEndpoint(baseUrl).build();

        assertThatThrownBy(() -> caches.getCache("cachedContents/test")).hasMessageContaining("test error");

        assertThat(serverTimeoutHeader.get()).isEqualTo("1200");
    }

    @Test
    void should_use_configured_timeout_for_caches() {
        respondWithError();
        GoogleGenAiCaches caches = GoogleGenAiCaches.builder()
                .apiKey("test-key")
                .apiEndpoint(baseUrl)
                .timeout(ofSeconds(30))
                .build();

        assertThatThrownBy(() -> caches.getCache("cachedContents/test")).hasMessageContaining("test error");

        assertThat(serverTimeoutHeader.get()).isEqualTo("30");
    }

    @Test
    void should_fail_at_build_time_when_timeout_is_zero() {
        assertThatThrownBy(() -> GoogleGenAiChatModel.builder()
                        .apiKey("test-key")
                        .modelName("gemini-test")
                        .timeout(Duration.ZERO)
                        .build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("timeout must be between 1 millisecond and 24 days, but was PT0S");
    }

    @Test
    void should_fail_instead_of_hanging_when_server_does_not_respond() {
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            awaitRelease();
        });
        ChatModel model = GoogleGenAiChatModel.builder()
                .apiKey("test-key")
                .apiEndpoint(baseUrl)
                .modelName("gemini-test")
                .timeout(ofSeconds(1))
                .maxRetries(0)
                .build();

        // the tests above show that the timeout reaches the Google GenAI SDK, this one shows that the SDK enforces it.
        // after the timeout, the SDK still retries the cancelled call a few times with a backoff
        // (about 15 s in total) before it gives up
        assertTimeoutPreemptively(ofSeconds(60), () -> assertThatThrownBy(() -> model.chat("Hi"))
                .isExactlyInstanceOf(TimeoutException.class));
    }

    @Test
    void should_fail_instead_of_hanging_when_stream_stops() throws Exception {
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            OutputStream body = exchange.getResponseBody();
            body.write(("data: " + PARTIAL_RESPONSE.strip() + "\n\n").getBytes(UTF_8));
            body.flush();
            awaitRelease();
        });
        StreamingChatModel model = GoogleGenAiStreamingChatModel.builder()
                .apiKey("test-key")
                .apiEndpoint(baseUrl)
                .modelName("gemini-test")
                .timeout(ofSeconds(1))
                .build();
        StreamingHandler handler = new StreamingHandler();

        model.chat("Hi", handler);

        // the timeout limits the whole stream, not only the time until the first response
        assertThat(handler.error.get(30, SECONDS)).isExactlyInstanceOf(TimeoutException.class);
        assertThat(handler.partialResponses).containsExactly("Hel");
    }

    private void respondWithError() {
        server.createContext("/", exchange -> {
            serverTimeoutHeader.set(exchange.getRequestHeaders().getFirst("X-Server-Timeout"));
            exchange.getRequestBody().readAllBytes();
            respond(exchange, 400, ERROR_RESPONSE);
        });
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private void awaitRelease() {
        try {
            releaseStalledResponses.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static class StreamingHandler implements StreamingChatResponseHandler {

        final List<String> partialResponses = new CopyOnWriteArrayList<>();
        final CompletableFuture<Throwable> error = new CompletableFuture<>();

        @Override
        public void onPartialResponse(String partialResponse) {
            partialResponses.add(partialResponse);
        }

        @Override
        public void onCompleteResponse(ChatResponse completeResponse) {
            error.completeExceptionally(new AssertionError("expected an error, but the response completed"));
        }

        @Override
        public void onError(Throwable throwable) {
            error.complete(throwable);
        }
    }
}
