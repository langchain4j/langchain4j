package dev.langchain4j.mcp.client.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.mcp.client.progress.McpProgressNotification;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.protocol.McpCallToolRequest;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StreamableHttpMcpTransportBackpressureTest {

    private static final int PROGRESS_COUNT = 64;
    private static final String RESULT = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"content\":[]}}";

    private final CountDownLatch callbackEntered = new CountDownLatch(1);
    private final CountDownLatch releaseCallback = new CountDownLatch(1);
    private final CountDownLatch callbackReturned = new CountDownLatch(1);
    private final CompletableFuture<Void> burstWritten = new CompletableFuture<>();
    private final List<McpProgressNotification> progress = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private StreamableHttpMcpTransport transport;
    private ExecutorService executor;

    @AfterEach
    void tearDown() throws Exception {
        releaseCallback.countDown();
        if (server != null) {
            server.stop(0);
        }
        if (transport != null) {
            transport.close();
        }
        if (executor != null) {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @ParameterizedTest(name = "with executor: {0}")
    @ValueSource(booleans = {true, false})
    void should_preserve_progress_and_result_when_the_callback_cannot_keep_up_with_an_sse_burst(boolean withExecutor)
            throws Exception {
        CompletableFuture<String> response = startRequest(withExecutor);
        awaitBurstWhileCallbackIsPaused(response);

        releaseCallback.countDown();

        assertThat(response.get(10, TimeUnit.SECONDS)).isEqualTo(" " + RESULT);
        assertThat(progress)
                .extracting(McpProgressNotification::progress)
                .containsExactlyElementsOf(IntStream.range(0, PROGRESS_COUNT)
                        .mapToObj(value -> (double) value)
                        .toList());
        assertThat(progress).extracting(McpProgressNotification::progressToken).containsOnly("progress-1");
    }

    @ParameterizedTest(name = "with executor: {0}")
    @ValueSource(booleans = {true, false})
    void should_cancel_while_a_progress_callback_is_busy_without_delivering_queued_notifications(boolean withExecutor)
            throws Exception {
        CompletableFuture<String> response = startRequest(withExecutor);
        awaitBurstWhileCallbackIsPaused(response);

        assertThat(response.cancel(true)).isTrue();
        releaseCallback.countDown();
        assertThat(callbackReturned.await(10, TimeUnit.SECONDS)).isTrue();
        transport.close();
        if (executor != null) {
            executor.shutdown();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(response).isCancelled();
        assertThat(progress).extracting(McpProgressNotification::progress).containsExactly(0d);
    }

    private void awaitBurstWhileCallbackIsPaused(CompletableFuture<String> response) throws Exception {
        assertThat(callbackEntered.await(10, TimeUnit.SECONDS)).isTrue();
        burstWritten.get(10, TimeUnit.SECONDS);
        // Socket writes can finish before the HTTP client reads them. Keep the callback paused for
        // this bounded interval so an eager reader can fill its event buffer, and assert that the
        // operation stays pending until the callback is allowed to consume the remaining events.
        assertThatThrownBy(() -> response.get(500, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
    }

    private CompletableFuture<String> startRequest(boolean withExecutor) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/mcp", this::respond);
        server.start();
        StreamableHttpMcpTransport.Builder builder = StreamableHttpMcpTransport.builder()
                .url("http://localhost:" + server.getAddress().getPort() + "/mcp")
                .setHttpVersion1_1();
        if (withExecutor) {
            executor = Executors.newFixedThreadPool(4);
            builder.executor(executor);
        }
        transport = builder.build();
        transport.setModernProtocol(true);
        transport.start(new McpOperationHandler(
                new ConcurrentHashMap<>(),
                List::of,
                transport,
                null,
                () -> {},
                null,
                () -> {},
                null,
                notification -> {
                    progress.add(notification);
                    if (notification.progress() == 0) {
                        callbackEntered.countDown();
                        try {
                            await(releaseCallback);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        } finally {
                            callbackReturned.countDown();
                        }
                    }
                },
                null,
                null,
                null,
                null));
        return transport.sendRequest(new McpCallToolRequest(1L, "slow-tool", Map.of(), "progress-1"));
    }

    private void respond(HttpExchange exchange) throws IOException {
        try {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write(notification(0).getBytes(StandardCharsets.UTF_8));
            exchange.getResponseBody().flush();
            await(callbackEntered);

            // More lines than the HTTP SPI's default event buffer, followed by real notifications
            // and a result: comments must not make a temporarily busy progress callback lose either.
            StringBuilder burst = new StringBuilder(": keepalive\n".repeat(30_000));
            for (int i = 1; i < PROGRESS_COUNT; i++) {
                burst.append(notification(i));
            }
            burst.append("data: ").append(RESULT).append("\n\n");
            exchange.getResponseBody().write(burst.toString().getBytes(StandardCharsets.UTF_8));
            exchange.getResponseBody().flush();
            burstWritten.complete(null);
        } catch (IOException | RuntimeException e) {
            burstWritten.completeExceptionally(e);
            throw e;
        } finally {
            exchange.close();
        }
    }

    private static String notification(int progress) {
        return "data: {\"jsonrpc\":\"2.0\",\"method\":\"notifications/progress\","
                + "\"params\":{\"progressToken\":\"progress-1\",\"progress\":" + progress + "}}\n\n";
    }

    private static void await(CountDownLatch latch) throws IOException {
        try {
            if (!latch.await(30, TimeUnit.SECONDS)) {
                throw new IOException("Timed out waiting for the progress callback");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }
}
