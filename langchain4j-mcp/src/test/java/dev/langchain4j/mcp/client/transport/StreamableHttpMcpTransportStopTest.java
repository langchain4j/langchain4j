package dev.langchain4j.mcp.client.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class StreamableHttpMcpTransportStopTest {

    private static final String LEGACY_PROTOCOL = "2025-11-25";

    private HttpServer server;
    private volatile boolean serverReady;
    private final AtomicInteger subsidiaryChannelRequests = new AtomicInteger();

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void a_transport_of_a_failed_build_should_be_usable_for_another_attempt() throws Exception {
        // given: a server that cannot initialize yet
        StreamableHttpMcpTransport transport =
                transportBuilder().url(startServer()).build();
        DefaultMcpClient.Builder clientBuilder = new DefaultMcpClient.Builder()
                .transport(transport)
                .protocolVersion(LEGACY_PROTOCOL)
                .autoHealthCheck(false);
        assertThatThrownBy(clientBuilder::build).isInstanceOf(RuntimeException.class);

        // when: the server is back
        serverReady = true;

        // then: the same transport works
        assertThatCode(() -> clientBuilder.build().close()).doesNotThrowAnyException();
    }

    @Test
    void stop_should_cancel_an_already_scheduled_reconnect_of_the_subsidiary_channel() throws Exception {
        // given: a server that ends the subsidiary channel and asks for a reconnect in 1.5 seconds
        serverReady = true;
        StreamableHttpMcpTransport transport = transportBuilder()
                .url(startServer())
                .subsidiaryChannel(true)
                .build();
        try (McpClient ignored = new DefaultMcpClient.Builder()
                .transport(transport)
                .protocolVersion(LEGACY_PROTOCOL)
                .autoHealthCheck(false)
                .build()) {
            assertThat(subsidiaryChannelRequests).hasValue(1);
            Thread.sleep(300); // lets the client see the end of the channel and schedule the reconnect

            // when
            transport.stop();
            Thread.sleep(2_000);

            // then
            assertThat(subsidiaryChannelRequests).hasValue(1);
        }
    }

    private static StreamableHttpMcpTransport.Builder transportBuilder() {
        return StreamableHttpMcpTransport.builder().setHttpVersion1_1();
    }

    private String startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/mcp", exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                subsidiaryChannelRequests.incrementAndGet();
                respond(exchange, 200, "text/event-stream", "retry: 1500\n\n");
                return;
            }
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, Object> message = McpJson.toMap(body);
            if (!"initialize".equals(message.get("method"))) {
                respond(exchange, 200, "application/json", "{}");
            } else if (serverReady) {
                respond(exchange, 200, "application/json", initializeResult(message.get("id")));
            } else {
                respond(exchange, 500, "application/json", "{}");
            }
        });
        server.start();
        return "http://localhost:" + server.getAddress().getPort() + "/mcp";
    }

    private static String initializeResult(Object id) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + ((Number) id).longValue() + ",\"result\":{\"protocolVersion\":\""
                + LEGACY_PROTOCOL + "\",\"capabilities\":{},\"serverInfo\":{\"name\":\"test-server\",\"version\":\"1.0\"}}}";
    }

    private static void respond(HttpExchange exchange, int status, String contentType, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
