package dev.langchain4j.mcp.client.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.mcp.client.auth.McpAuthChallenge;
import dev.langchain4j.mcp.client.auth.McpAuthProvider;
import dev.langchain4j.mcp.client.auth.McpAuthRequest;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.protocol.McpInitializeRequest;
import dev.langchain4j.mcp.protocol.McpListToolsRequest;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class StreamableHttpMcpTransportAuthTest {

    private static final String INITIALIZE_RESULT =
            "{\"jsonrpc\":\"2.0\",\"id\":0,\"result\":{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{},\"serverInfo\":{\"name\":\"test-server\",\"version\":\"1.0\"}}}";

    private static final String TOOLS_LIST_RESULT = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":[]}}";

    private static final String CHALLENGE =
            "Bearer resource_metadata=\"http://localhost/.well-known/oauth-protected-resource\","
                    + " scope=\"tools:read\", error=\"invalid_token\"";

    /** One entry per HTTP request the server saw: "METHOD method-or-GET Authorization-header-values". */
    private final List<String> requests = new CopyOnWriteArrayList<>();

    /** How many subsidiary channel (GET) requests the server rejects with 401 before serving them. */
    private final AtomicInteger subsidiaryRejections = new AtomicInteger();

    /** Whether the server serves the subsidiary SSE channel at all (otherwise 405, as many servers do). */
    private volatile boolean subsidiaryChannelAvailable;

    private final List<HttpExchange> openSseExchanges = new CopyOnWriteArrayList<>();

    private HttpServer server;
    private StreamableHttpMcpTransport transport;

    /**
     * Starts a server that accepts requests carrying {@code Authorization: Bearer <acceptedToken>}
     * and rejects any other request with 401 and a Bearer challenge.
     */
    private void startServer(String acceptedToken) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/mcp", exchange -> {
            List<String> authorization = exchange.getRequestHeaders().get("Authorization");
            if ("GET".equals(exchange.getRequestMethod())) {
                requests.add("GET subsidiary " + authorization);
                if (subsidiaryRejections.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                    exchange.getResponseHeaders().set("WWW-Authenticate", CHALLENGE);
                    respond(exchange, 401, "");
                } else if (subsidiaryChannelAvailable) {
                    exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
                    exchange.sendResponseHeaders(200, 0);
                    OutputStream out = exchange.getResponseBody();
                    out.write(": ready\n\n".getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    openSseExchanges.add(exchange); // kept open until the test ends
                } else {
                    respond(exchange, 405, "");
                }
                return;
            }
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String method = McpJson.parse(body).path("method").asText();
            requests.add("POST " + method + " " + authorization);
            if (authorization == null || !authorization.equals(List.of("Bearer " + acceptedToken))) {
                exchange.getResponseHeaders().set("WWW-Authenticate", CHALLENGE);
                respond(exchange, 401, "");
            } else if ("initialize".equals(method)) {
                exchange.getResponseHeaders().set("Mcp-Session-Id", "session-1");
                respond(exchange, 200, INITIALIZE_RESULT);
            } else if ("notifications/initialized".equals(method)) {
                respond(exchange, 200, "{}");
            } else {
                respond(exchange, 200, TOOLS_LIST_RESULT);
            }
        });
        server.start();
    }

    private void startTransport(StreamableHttpMcpTransport.Builder builder) {
        transport = builder.url("http://localhost:" + server.getAddress().getPort() + "/mcp")
                .setHttpVersion1_1()
                .build();
        transport.start(new McpOperationHandler(
                new ConcurrentHashMap<>(),
                () -> Collections.emptyList(),
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

    private static void respond(HttpExchange exchange, int statusCode, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0) {
            exchange.sendResponseHeaders(statusCode, -1);
            exchange.close();
            return;
        }
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @AfterEach
    void tearDown() throws IOException {
        if (transport != null) {
            transport.close();
        }
        openSseExchanges.forEach(HttpExchange::close);
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void authorization_header_is_sent_on_every_request() throws Exception {
        startServer("t1");
        startTransport(StreamableHttpMcpTransport.builder().authProvider(McpAuthProvider.bearer("t1")));

        transport.sendInitializeRequest(new McpInitializeRequest(0L)).get(5, TimeUnit.SECONDS);
        String response =
                transport.sendRequest(new McpListToolsRequest(1L, null)).get(5, TimeUnit.SECONDS);

        assertThat(response).contains("\"tools\":[]");
        assertThat(requests)
                .containsExactly(
                        "POST initialize [Bearer t1]",
                        "POST notifications/initialized [Bearer t1]",
                        "POST tools/list [Bearer t1]");
    }

    @Test
    void provider_receives_the_request_it_authorizes() throws Exception {
        startServer("t1");
        List<McpAuthRequest> seen = new CopyOnWriteArrayList<>();
        startTransport(StreamableHttpMcpTransport.builder().authProvider(request -> {
            seen.add(request);
            return "Bearer t1";
        }));

        transport.sendInitializeRequest(new McpInitializeRequest(0L)).get(5, TimeUnit.SECONDS);
        transport.sendRequest(new McpListToolsRequest(1L, null)).get(5, TimeUnit.SECONDS);

        assertThat(seen).hasSize(3);
        assertThat(seen.get(0).method()).isEqualTo("POST");
        assertThat(seen.get(0).uri().getPath()).isEqualTo("/mcp");
        assertThat(seen.get(0).callContext().message()).isInstanceOf(McpInitializeRequest.class);
        assertThat(seen.get(2).callContext().message()).isInstanceOf(McpListToolsRequest.class);
    }

    @Test
    void provider_replaces_authorization_from_custom_headers() throws Exception {
        startServer("t1");
        startTransport(StreamableHttpMcpTransport.builder()
                .customHeaders(Map.of("Authorization", "Bearer stale", "X-Tenant", "acme"))
                .authProvider(McpAuthProvider.bearer("t1")));

        transport.sendInitializeRequest(new McpInitializeRequest(0L)).get(5, TimeUnit.SECONDS);

        assertThat(requests.get(0)).isEqualTo("POST initialize [Bearer t1]");
    }

    @Test
    void null_authorization_sends_no_header_and_is_rejected() throws Exception {
        startServer("t1");
        startTransport(StreamableHttpMcpTransport.builder().authProvider(McpAuthProvider.bearer(() -> null)));

        CompletableFuture<String> response = transport.sendInitializeRequest(new McpInitializeRequest(0L));

        assertThat(failureOf(response).statusCode()).isEqualTo(401);
        assertThat(requests).containsExactly("POST initialize null");
    }

    @Test
    void challenge_is_handed_to_the_provider_and_the_request_is_retried_once() throws Exception {
        startServer("fresh");
        AtomicReference<String> token = new AtomicReference<>("expired");
        AtomicReference<McpAuthChallenge> challenge = new AtomicReference<>();
        startTransport(StreamableHttpMcpTransport.builder().authProvider(new McpAuthProvider() {

            @Override
            public String getAuthorization(McpAuthRequest request) {
                return "Bearer " + token.get();
            }

            @Override
            public boolean onChallenge(McpAuthChallenge c) {
                challenge.set(c);
                token.set("fresh");
                return true;
            }
        }));

        transport.sendInitializeRequest(new McpInitializeRequest(0L)).get(5, TimeUnit.SECONDS);
        String response =
                transport.sendRequest(new McpListToolsRequest(1L, null)).get(5, TimeUnit.SECONDS);

        assertThat(response).contains("\"tools\":[]");
        assertThat(requests)
                .containsExactly(
                        "POST initialize [Bearer expired]",
                        "POST initialize [Bearer fresh]",
                        "POST notifications/initialized [Bearer fresh]",
                        "POST tools/list [Bearer fresh]");
        assertThat(challenge.get().statusCode()).isEqualTo(401);
        assertThat(challenge.get().uri().getPath()).isEqualTo("/mcp");
        assertThat(challenge.get().resourceMetadata())
                .isEqualTo("http://localhost/.well-known/oauth-protected-resource");
        assertThat(challenge.get().scopes()).containsExactly("tools:read");
        assertThat(challenge.get().error()).isEqualTo("invalid_token");
    }

    @Test
    void rejection_propagates_when_the_provider_declines_the_challenge() throws Exception {
        startServer("t1");
        startTransport(StreamableHttpMcpTransport.builder().authProvider(McpAuthProvider.bearer("wrong")));

        CompletableFuture<String> response = transport.sendInitializeRequest(new McpInitializeRequest(0L));

        assertThat(failureOf(response).statusCode()).isEqualTo(401);
        assertThat(requests).containsExactly("POST initialize [Bearer wrong]");
    }

    @Test
    void a_request_is_retried_at_most_once() throws Exception {
        startServer("t1");
        startTransport(StreamableHttpMcpTransport.builder().authProvider(new McpAuthProvider() {

            @Override
            public String getAuthorization(McpAuthRequest request) {
                return "Bearer still-wrong";
            }

            @Override
            public boolean onChallenge(McpAuthChallenge challenge) {
                return true;
            }
        }));

        CompletableFuture<String> response = transport.sendInitializeRequest(new McpInitializeRequest(0L));

        assertThat(failureOf(response).statusCode()).isEqualTo(401);
        assertThat(requests)
                .containsExactly("POST initialize [Bearer still-wrong]", "POST initialize [Bearer still-wrong]");
    }

    @Test
    void provider_failure_fails_the_request_instead_of_hanging() throws Exception {
        startServer("t1");
        startTransport(StreamableHttpMcpTransport.builder().authProvider(request -> {
            throw new IllegalStateException("token endpoint unreachable");
        }));

        CompletableFuture<String> response = transport.sendInitializeRequest(new McpInitializeRequest(0L));

        Throwable thrown = catchThrowable(() -> response.get(5, TimeUnit.SECONDS));
        assertThat(thrown).isInstanceOf(ExecutionException.class).hasCauseInstanceOf(IllegalStateException.class);
        assertThat(requests).isEmpty();
    }

    @Test
    void subsidiary_channel_carries_the_authorization_header() throws Exception {
        startServer("t1");
        startTransport(StreamableHttpMcpTransport.builder()
                .subsidiaryChannel(true)
                .authProvider(McpAuthProvider.bearer("t1")));

        transport.sendInitializeRequest(new McpInitializeRequest(0L)).get(5, TimeUnit.SECONDS);

        assertThat(requests).contains("GET subsidiary [Bearer t1]");
    }

    @Test
    void subsidiary_channel_challenge_is_handed_to_the_provider_and_the_channel_is_reopened() throws Exception {
        startServer("t1");
        subsidiaryChannelAvailable = true;
        subsidiaryRejections.set(1);
        AtomicReference<McpAuthChallenge> seen = new AtomicReference<>();
        startTransport(
                StreamableHttpMcpTransport.builder().subsidiaryChannel(true).authProvider(new McpAuthProvider() {
                    @Override
                    public String getAuthorization(McpAuthRequest request) {
                        return "Bearer t1";
                    }

                    @Override
                    public boolean onChallenge(McpAuthChallenge challenge) {
                        seen.set(challenge);
                        return true;
                    }
                }));

        transport.sendInitializeRequest(new McpInitializeRequest(0L)).get(5, TimeUnit.SECONDS);

        assertThat(requests.stream().filter(r -> r.startsWith("GET subsidiary")))
                .containsExactly("GET subsidiary [Bearer t1]", "GET subsidiary [Bearer t1]");
        assertThat(seen.get()).isNotNull();
        assertThat(seen.get().statusCode()).isEqualTo(401);
        assertThat(seen.get().scopes()).containsExactly("tools:read");
    }

    @Test
    void subsidiary_channel_challenge_declined_by_the_provider_is_not_retried() throws Exception {
        startServer("t1");
        subsidiaryChannelAvailable = true;
        subsidiaryRejections.set(1);
        startTransport(StreamableHttpMcpTransport.builder()
                .subsidiaryChannel(true)
                .authProvider(McpAuthProvider.bearer("t1")));

        transport.sendInitializeRequest(new McpInitializeRequest(0L)).get(5, TimeUnit.SECONDS);

        assertThat(requests.stream().filter(r -> r.startsWith("GET subsidiary")))
                .containsExactly("GET subsidiary [Bearer t1]");
    }

    private static HttpException failureOf(CompletableFuture<String> response) {
        Throwable thrown = catchThrowable(() -> response.get(5, TimeUnit.SECONDS));
        assertThat(thrown).isInstanceOf(ExecutionException.class).hasCauseInstanceOf(HttpException.class);
        return (HttpException) thrown.getCause();
    }
}
