package dev.langchain4j.mcp.client.transport;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The data of an SSE event may span several {@code data:} lines, which are joined with a line feed and
 * dispatched as one message when the blank line ends the event.
 */
class StreamableHttpMcpTransportSseDataLinesTest {

    private HttpServer server;
    private StreamableHttpMcpTransport transport;
    private final Map<Long, CompletableFuture<String>> pendingOperations = new ConcurrentHashMap<>();

    private void startServer(String eventStream) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/mcp", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            byte[] bytes = eventStream.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        transport = StreamableHttpMcpTransport.builder()
                .url("http://localhost:" + server.getAddress().getPort() + "/mcp")
                .setHttpVersion1_1()
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
    void joins_data_lines_of_one_event() throws Exception {
        startServer("event: message\n"
                + "data: {\"jsonrpc\":\"2.0\",\n"
                + "data: \"id\":7,\n"
                + "data: \"result\":{\"tools\":[]}}\n"
                + "\n");

        CompletableFuture<String> response = transport.sendRequest(new McpListToolsRequest(7L, null));

        assertThat(response.get(5, TimeUnit.SECONDS))
                .isEqualTo(" {\"jsonrpc\":\"2.0\",\n \"id\":7,\n \"result\":{\"tools\":[]}}");
    }

    @Test
    void joins_data_lines_with_crlf_line_endings() throws Exception {
        startServer("event: message\r\n"
                + "data:{\"jsonrpc\":\"2.0\",\"id\":7,\r\n"
                + "data:\"result\":{\"tools\":[]}}\r\n"
                + "\r\n");

        CompletableFuture<String> response = transport.sendRequest(new McpListToolsRequest(7L, null));

        assertThat(response.get(5, TimeUnit.SECONDS))
                .isEqualTo("{\"jsonrpc\":\"2.0\",\"id\":7,\n\"result\":{\"tools\":[]}}");
    }

    @Test
    void dispatches_an_event_whose_stream_ends_without_a_blank_line() throws Exception {
        startServer("event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":7,\"result\":{\"tools\":[]}}\n");

        CompletableFuture<String> response = transport.sendRequest(new McpListToolsRequest(7L, null));

        assertThat(response.get(5, TimeUnit.SECONDS))
                .isEqualTo(" {\"jsonrpc\":\"2.0\",\"id\":7,\"result\":{\"tools\":[]}}");
    }
}
