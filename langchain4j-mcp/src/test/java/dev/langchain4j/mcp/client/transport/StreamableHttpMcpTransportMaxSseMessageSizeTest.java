package dev.langchain4j.mcp.client.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.protocol.McpListToolsRequest;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * A server must not be able to make the client buffer an unbounded amount of data
 * by sending an SSE message that never ends.
 */
class StreamableHttpMcpTransportMaxSseMessageSizeTest {

    private static final int LIMIT = 256 * 1024;
    private static final String RESPONSE_PREFIX =
            "{\"jsonrpc\":\"2.0\",\"id\":7,\"result\":{\"tools\":[],\"padding\":\"";
    private static final String RESPONSE_SUFFIX = "\"}}";

    private HttpServer server;
    private StreamableHttpMcpTransport transport;
    private final Map<Long, CompletableFuture<String>> pendingOperations = new ConcurrentHashMap<>();

    @FunctionalInterface
    interface ResponseWriter {
        void write(OutputStream out) throws IOException;
    }

    private void startServer(String contentType, ResponseWriter writer) throws IOException {
        startServer(contentType, writer, LIMIT);
    }

    private void startServer(String contentType, ResponseWriter writer, Integer maxSseMessageSize)
            throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/mcp", exchange -> respond(exchange, contentType, writer));
        server.start();
        transport = StreamableHttpMcpTransport.builder()
                .url("http://localhost:" + server.getAddress().getPort() + "/mcp")
                .setHttpVersion1_1()
                .maxSseMessageSize(maxSseMessageSize)
                .build();
        transport.start(new McpOperationHandler(
                pendingOperations,
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
                null));
    }

    private static void respond(HttpExchange exchange, String contentType, ResponseWriter writer) throws IOException {
        exchange.getRequestBody().readAllBytes();
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, 0);
        try (OutputStream out = exchange.getResponseBody()) {
            writer.write(out);
        } catch (IOException e) {
            // the client closed the stream
        }
    }

    /**
     * Writes the given chunk until the client closes the connection (or a safety cap far above the limit is reached).
     */
    private static void writeEndlessly(OutputStream out, String chunk) throws IOException {
        byte[] bytes = chunk.getBytes(StandardCharsets.UTF_8);
        for (long written = 0; written < 64L * LIMIT; written += bytes.length) {
            out.write(bytes);
            out.flush();
        }
    }

    @AfterEach
    void tearDown() throws IOException {
        if (transport != null) {
            transport.close();
        }
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void delivers_a_message_spanning_several_line_fragments_below_the_limit() throws Exception {
        String padding = "x".repeat(LIMIT / 2);
        startServer(
                "text/event-stream",
                out -> out.write(("data:" + RESPONSE_PREFIX + padding + RESPONSE_SUFFIX + "\n\n")
                        .getBytes(StandardCharsets.UTF_8)));

        CompletableFuture<String> response = transport.sendRequest(new McpListToolsRequest(7L, null));

        assertThat(response.get(10, TimeUnit.SECONDS)).isEqualTo(RESPONSE_PREFIX + padding + RESPONSE_SUFFIX);
    }

    @Test
    void fails_on_a_line_that_never_ends() throws Exception {
        startServer("text/event-stream", out -> {
            out.write("data: ".getBytes(StandardCharsets.UTF_8));
            writeEndlessly(out, "x".repeat(8192));
        });

        CompletableFuture<String> response = transport.sendRequest(new McpListToolsRequest(7L, null));

        assertThatThrownBy(() -> response.get(10, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exceeds the maximum size of " + LIMIT + " bytes");
    }

    @Test
    void limits_messages_to_20_mib_by_default() throws Exception {
        int defaultLimit = 20 * 1024 * 1024;
        startServer(
                "text/event-stream",
                out -> {
                    out.write("data: ".getBytes(StandardCharsets.UTF_8));
                    byte[] chunk = "x".repeat(8192).getBytes(StandardCharsets.UTF_8);
                    for (int written = 0; written <= defaultLimit; written += chunk.length) {
                        out.write(chunk);
                    }
                    out.write("\n\n".getBytes(StandardCharsets.UTF_8));
                },
                null);

        CompletableFuture<String> response = transport.sendRequest(new McpListToolsRequest(7L, null));

        assertThatThrownBy(() -> response.get(10, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exceeds the maximum size of " + defaultLimit + " bytes");
    }

    @Test
    void fails_on_an_event_that_never_ends() throws Exception {
        startServer("text/event-stream", out -> writeEndlessly(out, "data: " + "x".repeat(1024) + "\n"));

        CompletableFuture<String> response = transport.sendRequest(new McpListToolsRequest(7L, null));

        assertThatThrownBy(() -> response.get(10, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exceeds the maximum size of " + LIMIT + " bytes");
    }

    @Test
    void limits_each_event_separately() throws Exception {
        String event = "data: " + "x".repeat(LIMIT / 2) + "\n\n";
        startServer("text/event-stream", out -> {
            for (int i = 0; i < 8; i++) {
                out.write(event.getBytes(StandardCharsets.UTF_8));
            }
            out.write(("data:" + RESPONSE_PREFIX + RESPONSE_SUFFIX + "\n\n").getBytes(StandardCharsets.UTF_8));
        });

        CompletableFuture<String> response = transport.sendRequest(new McpListToolsRequest(7L, null));

        assertThat(response.get(10, TimeUnit.SECONDS)).isEqualTo(RESPONSE_PREFIX + RESPONSE_SUFFIX);
    }

    @Test
    void does_not_limit_json_responses() throws Exception {
        String padding = "x".repeat(LIMIT * 2);
        startServer(
                "application/json",
                out -> out.write((RESPONSE_PREFIX + padding + RESPONSE_SUFFIX).getBytes(StandardCharsets.UTF_8)));

        CompletableFuture<String> response = transport.sendRequest(new McpListToolsRequest(7L, null));

        assertThat(response.get(10, TimeUnit.SECONDS)).isEqualTo(RESPONSE_PREFIX + padding + RESPONSE_SUFFIX);
    }

    @Test
    void rejects_a_non_positive_limit() {
        assertThatThrownBy(() -> StreamableHttpMcpTransport.builder()
                        .url("http://localhost/mcp")
                        .maxSseMessageSize(0)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxSseMessageSize");
    }
}
