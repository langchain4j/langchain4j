package dev.langchain4j.mcp.client.transport;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.protocol.McpListToolsRequest;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StreamableHttpMcpTransportExecutorTest {

    private static final String EXECUTOR_THREAD_PREFIX = "mcp-configured-executor-";
    private static final String TOOLS_RESULT = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":[]}}";

    private final CountDownLatch releaseResponse = new CountDownLatch(1);
    private final CountDownLatch finishResponse = new CountDownLatch(1);
    private final CompletableFuture<Map<String, String>> requestHeaders = new CompletableFuture<>();
    private final CompletableFuture<String> handlerThread = new CompletableFuture<>();
    private HttpServer server;
    private StreamableHttpMcpTransport transport;
    private ExecutorService executor;

    @AfterEach
    void tearDown() throws Exception {
        releaseResponse.countDown();
        finishResponse.countDown();
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

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void should_dispatch_messages_and_result_callbacks_on_the_configured_executor(boolean sse) throws Exception {
        startServer(200, sse, false);

        CompletableFuture<String> response = transport.sendRequest(new McpListToolsRequest(1L, null));
        CompletableFuture<String> callbackThread = response.thenApply(result -> {
            assertThat(result).isEqualTo(sse ? " " + TOOLS_RESULT : TOOLS_RESULT);
            return Thread.currentThread().getName();
        });
        releaseResponse.countDown();

        assertThat(handlerThread.get(10, TimeUnit.SECONDS)).startsWith(EXECUTOR_THREAD_PREFIX);
        assertThat(callbackThread.get(10, TimeUnit.SECONDS)).startsWith(EXECUTOR_THREAD_PREFIX);
        assertThat(requestHeaders.get(10, TimeUnit.SECONDS))
                .containsEntry("Authorization", "Bearer user-token")
                .containsEntry("Mcp-Session-Id", "request-session");
        assertThat(transport.getMcpSessionId()).isEqualTo("response-session");
    }

    @Test
    void should_dispatch_error_callbacks_on_the_configured_executor() throws Exception {
        startServer(401, false, false);

        CompletableFuture<String> callbackThread = new CompletableFuture<>();
        CompletableFuture<Throwable> failure = new CompletableFuture<>();
        transport.sendRequest(new McpListToolsRequest(1L, null)).whenComplete((result, error) -> {
            failure.complete(error);
            callbackThread.complete(Thread.currentThread().getName());
        });
        releaseResponse.countDown();

        assertThat(callbackThread.get(10, TimeUnit.SECONDS)).startsWith(EXECUTOR_THREAD_PREFIX);
        assertThat(failure.get(10, TimeUnit.SECONDS))
                .isInstanceOfSatisfying(
                        HttpException.class,
                        error -> assertThat(error.statusCode()).isEqualTo(401));
        assertThat(handlerThread).isNotDone();
    }

    @Test
    void should_allow_closing_the_transport_from_a_result_callback_while_the_stream_is_open() throws Exception {
        startServer(200, true, true);

        CompletableFuture<String> closed = transport
                .sendRequest(new McpListToolsRequest(1L, null))
                .thenApply(result -> {
                    try {
                        transport.close();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    return Thread.currentThread().getName();
                });
        releaseResponse.countDown();

        try {
            assertThat(closed.get(10, TimeUnit.SECONDS)).startsWith(EXECUTOR_THREAD_PREFIX);
        } finally {
            finishResponse.countDown();
        }
    }

    private void startServer(int status, boolean sse, boolean keepStreamOpen) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/mcp", exchange -> respond(exchange, status, sse, keepStreamOpen));
        server.start();
        AtomicInteger threadCounter = new AtomicInteger();
        executor = Executors.newFixedThreadPool(
                4, task -> new Thread(task, EXECUTOR_THREAD_PREFIX + threadCounter.incrementAndGet()));
        transport = StreamableHttpMcpTransport.builder()
                .url("http://localhost:" + server.getAddress().getPort() + "/mcp")
                .setHttpVersion1_1()
                .executor(executor)
                .customHeaders(Map.of("Authorization", "Bearer user-token"))
                .build();
        transport.setMcpSessionId("request-session");
        transport.start(
                new McpOperationHandler(
                        new ConcurrentHashMap<>(),
                        Collections::emptyList,
                        transport,
                        null,
                        () -> {},
                        null,
                        () -> {},
                        null,
                        null,
                        null,
                        null,
                        null,
                        null) {
                    @Override
                    public void onMessage(String message) {
                        handlerThread.complete(Thread.currentThread().getName());
                        super.onMessage(message);
                    }
                });
    }

    private void respond(HttpExchange exchange, int status, boolean sse, boolean keepStreamOpen) throws IOException {
        try {
            exchange.getRequestBody().readAllBytes();
            requestHeaders.complete(Map.of(
                    "Authorization", exchange.getRequestHeaders().getFirst("Authorization"),
                    "Mcp-Session-Id", exchange.getRequestHeaders().getFirst("Mcp-Session-Id")));
            await(releaseResponse);
            exchange.getResponseHeaders().set("Content-Type", sse ? "text/event-stream" : "application/json");
            exchange.getResponseHeaders().set("Mcp-Session-Id", "response-session");
            String body = status == 200 ? (sse ? "data: " + TOOLS_RESULT + "\n\n" : TOOLS_RESULT) : "{}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, keepStreamOpen ? 0 : bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.getResponseBody().flush();
            if (keepStreamOpen) {
                await(finishResponse);
            }
        } finally {
            exchange.close();
        }
    }

    private static void await(CountDownLatch latch) throws IOException {
        try {
            if (!latch.await(30, TimeUnit.SECONDS)) {
                throw new IOException("Timed out waiting to release the HTTP response");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }
}
