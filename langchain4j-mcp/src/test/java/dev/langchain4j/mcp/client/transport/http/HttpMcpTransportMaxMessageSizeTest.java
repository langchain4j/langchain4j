package dev.langchain4j.mcp.client.transport.http;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.mcp.client.transport.McpOperationHandler;
import dev.langchain4j.mcp.protocol.McpListToolsRequest;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import okio.Buffer;
import okio.BufferedSource;
import okio.Okio;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A server must not be able to make the client buffer an unbounded amount of data
 * by sending an SSE event that never ends.
 */
class HttpMcpTransportMaxMessageSizeTest {

    private static final int LIMIT = 256 * 1024;
    private static final int WAIT_SECONDS = 60;
    private static final String RESPONSE_PREFIX = "{\"jsonrpc\":\"2.0\",\"id\":7,\"result\":{\"tools\":[],\"padding\":\"";
    private static final String RESPONSE_SUFFIX = "\"}}";

    private ExecutorService serverExecutor;
    private HttpServer server;
    private HttpMcpTransport transport;
    private final Map<Long, CompletableFuture<JsonNode>> pendingOperations = new ConcurrentHashMap<>();
    private final CountDownLatch requestReceived = new CountDownLatch(1);
    private final CountDownLatch channelFailed = new CountDownLatch(1);
    private final CountDownLatch streamClosedByClient = new CountDownLatch(1);

    @FunctionalInterface
    interface EventWriter {
        void write(OutputStream out) throws IOException;
    }

    private CompletableFuture<JsonNode> sendRequestTo(Integer maxMessageSize, EventWriter writer) throws IOException {
        serverExecutor = Executors.newCachedThreadPool();
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.setExecutor(serverExecutor);
        server.createContext("/sse", exchange -> stream(exchange, writer));
        server.createContext("/messages", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
            requestReceived.countDown();
        });
        server.start();
        transport = HttpMcpTransport.builder()
                .sseUrl("http://localhost:" + server.getAddress().getPort() + "/sse")
                .maxMessageSize(maxMessageSize)
                .build();
        transport.onFailure(channelFailed::countDown);
        transport.start(new McpOperationHandler(pendingOperations, Collections::emptyList, transport, null, () -> {}));
        return transport.executeOperationWithResponse(new McpListToolsRequest(7L, null));
    }

    private void stream(HttpExchange exchange, EventWriter writer) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, 0);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write("event: endpoint\ndata: /messages\n\n".getBytes(UTF_8));
            out.flush();
            requestReceived.await(WAIT_SECONDS, TimeUnit.SECONDS);
            writer.write(out);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            streamClosedByClient.countDown();
        }
    }

    /**
     * Writes the given chunk until the client closes the connection. The cap is on time and not on the amount of data,
     * because on closing, OkHttp drains the rest of the response for a short while and could read a small amount to its end.
     */
    private static void writeEndlessly(OutputStream out, String chunk) throws IOException {
        byte[] bytes = chunk.getBytes(UTF_8);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        while (System.nanoTime() < deadline) {
            out.write(bytes);
            out.flush();
        }
    }

    private static byte[] messageEvent(String message, String lineEnding) {
        return ("event: message" + lineEnding + "data: " + message + lineEnding + lineEnding).getBytes(UTF_8);
    }

    private static String messageOfSize(int size) {
        return RESPONSE_PREFIX + "x".repeat(size - RESPONSE_PREFIX.length() - RESPONSE_SUFFIX.length())
                + RESPONSE_SUFFIX;
    }

    private void assertChannelClosedByClient() throws InterruptedException {
        assertThat(channelFailed.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        assertThat(streamClosedByClient.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (transport != null) {
            transport.close();
        }
        if (server != null) {
            server.stop(0);
        }
        if (serverExecutor != null) {
            serverExecutor.shutdownNow();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"\n", "\r\n"})
    void delivers_a_message_below_the_limit(String lineEnding) throws Exception {
        String message = messageOfSize(LIMIT / 2);

        CompletableFuture<JsonNode> response = sendRequestTo(LIMIT, out -> out.write(messageEvent(message, lineEnding)));

        assertThat(response.get(WAIT_SECONDS, TimeUnit.SECONDS).toString()).isEqualTo(message);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\n", "\r\n"})
    void accepts_a_message_of_exactly_the_maximum_size(String lineEnding) throws Exception {
        String message = messageOfSize(LIMIT);

        CompletableFuture<JsonNode> response = sendRequestTo(LIMIT, out -> out.write(messageEvent(message, lineEnding)));

        assertThat(response.get(WAIT_SECONDS, TimeUnit.SECONDS).toString()).isEqualTo(message);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\n", "\r\n"})
    void limits_each_event_separately(String lineEnding) throws Exception {
        String ignoredEvent = "event: ignored" + lineEnding + "data: " + "x".repeat(LIMIT / 2) + lineEnding + lineEnding;
        String message = RESPONSE_PREFIX + RESPONSE_SUFFIX;

        CompletableFuture<JsonNode> response = sendRequestTo(LIMIT, out -> {
            for (int i = 0; i < 8; i++) {
                out.write(ignoredEvent.getBytes(UTF_8));
            }
            out.write(messageEvent(message, lineEnding));
        });

        assertThat(response.get(WAIT_SECONDS, TimeUnit.SECONDS).toString()).isEqualTo(message);
    }

    @Test
    void closes_the_channel_on_a_line_that_never_ends() throws Exception {
        sendRequestTo(LIMIT, out -> {
            out.write("event: message\ndata: ".getBytes(UTF_8));
            writeEndlessly(out, "x".repeat(8192));
        });

        assertChannelClosedByClient();
    }

    @Test
    void closes_the_channel_on_short_data_lines_without_a_blank_line() throws Exception {
        sendRequestTo(LIMIT, out -> writeEndlessly(out, "data: " + "x".repeat(100) + "\n"));

        assertChannelClosedByClient();
    }

    @ParameterizedTest
    @ValueSource(strings = {"event: ", "id: ", "retry: "})
    void closes_the_channel_on_a_field_padded_with_carriage_returns(String field) throws Exception {
        sendRequestTo(LIMIT, out -> {
            out.write(field.getBytes(UTF_8));
            writeEndlessly(out, "\r".repeat(8192));
        });

        assertChannelClosedByClient();
    }

    @Test
    void closes_the_channel_on_a_message_over_the_maximum_size() throws Exception {
        sendRequestTo(LIMIT, out -> out.write(messageEvent(messageOfSize(LIMIT + 64), "\n")));

        assertThat(channelFailed.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void applies_a_custom_limit() throws Exception {
        sendRequestTo(1024, out -> out.write(messageEvent(messageOfSize(2048), "\n")));

        assertThat(channelFailed.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void limits_messages_to_20_mib_by_default() throws Exception {
        int defaultLimit = 20 * 1024 * 1024;

        sendRequestTo(null, out -> {
            out.write("event: message\ndata: ".getBytes(UTF_8));
            byte[] chunk = "x".repeat(8192).getBytes(UTF_8);
            for (int written = 0; written <= defaultLimit + 8192; written += chunk.length) {
                out.write(chunk);
            }
            out.write("\n\n".getBytes(UTF_8));
        });

        assertThat(channelFailed.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void accepts_a_message_of_20_mib_by_default() throws Exception {
        // whitespace pads the message, because Jackson rejects a single string of more than 20 million characters
        String start = "{\"jsonrpc\":\"2.0\",\"id\":7,\"result\":{\"tools\":[]}";
        String message = start + " ".repeat(20 * 1024 * 1024 - start.length() - 1) + "}";

        CompletableFuture<JsonNode> response = sendRequestTo(null, out -> out.write(messageEvent(message, "\n")));

        assertThat(response.get(WAIT_SECONDS, TimeUnit.SECONDS).toString()).isEqualTo(start + "}");
    }

    @Test
    void rejects_a_non_positive_limit() {
        assertThatThrownBy(() -> HttpMcpTransport.builder()
                        .sseUrl("http://localhost/sse")
                        .maxMessageSize(0)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxMessageSize");
    }

    @Test
    void fails_with_a_message_that_names_the_limit_and_the_option() {
        assertThatThrownBy(() -> readThrough("data: " + "x".repeat(LIMIT + 64) + "\n\n", LIMIT))
                .isInstanceOf(IOException.class)
                .hasMessage("Message from the MCP server exceeds the maximum size of " + LIMIT
                        + " bytes; use HttpMcpTransport.Builder.maxMessageSize to raise the limit");
    }

    @Test
    void treats_a_carriage_return_after_a_data_line_as_an_event_boundary() throws Exception {
        String event = "data: " + "x".repeat(LIMIT / 2) + "\r\r";
        String stream = event.repeat(8);

        assertThat(readThrough(stream, LIMIT)).isEqualTo(stream);
    }

    @Test
    void counts_a_data_line_ended_by_a_carriage_return_towards_its_event() {
        String lines = ("data: " + "x".repeat(LIMIT / 2) + "\r").repeat(3);

        assertThatThrownBy(() -> readThrough(lines, LIMIT)).isInstanceOf(IOException.class);
    }

    private static String readThrough(String stream, int maxMessageSize) throws IOException {
        try (BufferedSource limited =
                Okio.buffer(new MaxSseEventSizeSource(new Buffer().writeUtf8(stream), maxMessageSize))) {
            return limited.readUtf8();
        }
    }
}
