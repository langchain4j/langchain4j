package dev.langchain4j.mcp.client.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.http.client.apache.ApacheHttpClient;
import dev.langchain4j.http.client.okhttp.OkHttpClient;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpException;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.protocol.McpInitializeRequest;
import dev.langchain4j.mcp.protocol.McpListToolsRequest;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class StreamableHttpMcpTransportStatusCodeTest {

    private static final String INITIALIZE_RESULT =
            "{\"jsonrpc\":\"2.0\",\"id\":0,\"result\":{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{},\"serverInfo\":{\"name\":\"test-server\",\"version\":\"1.0\"}}}";

    private static final String TOOLS_LIST_RESULT = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":[]}}";

    // the answer of a legacy server built with the TypeScript SDK to a request without a valid session
    private static final String NO_VALID_SESSION =
            "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32000,\"message\":\"Bad Request: No valid session ID provided\"},\"id\":null}";

    private HttpServer server;
    private final ExecutorService serverExecutor = Executors.newCachedThreadPool();
    private StreamableHttpMcpTransport transport;
    private final CountDownLatch errorBodyReleased = new CountDownLatch(1);
    private final ExecutorService responseExecutor = Executors.newSingleThreadExecutor();
    private String errorContentType = "application/json";
    private String errorBody = "{}";
    private boolean useResponseExecutor;

    enum Backend {
        JDK,
        OKHTTP,
        APACHE;

        StreamableHttpMcpTransport.Builder configure(StreamableHttpMcpTransport.Builder builder) {
            return switch (this) {
                case JDK -> builder.setHttpVersion1_1();
                case OKHTTP -> builder.httpClientBuilder(OkHttpClient.builder().readTimeout(Duration.ZERO));
                case APACHE -> builder.httpClientBuilder(ApacheHttpClient.builder());
            };
        }
    }

    private void startServer(int toolsListStatus) throws IOException {
        startServer(toolsListStatus, false, Backend.JDK);
    }

    private void startServer(int toolsListStatus, boolean holdErrorBody, Backend backend) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.setExecutor(serverExecutor);
        server.createContext("/mcp", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String method = (String) McpJson.toMap(body).get("method");
            if ("initialize".equals(method)) {
                exchange.getResponseHeaders().set("Mcp-Session-Id", "session-1");
                respond(exchange, 200, INITIALIZE_RESULT);
            } else if ("notifications/initialized".equals(method)) {
                respond(exchange, 200, "{}");
            } else if (toolsListStatus == 200) {
                respond(exchange, 200, TOOLS_LIST_RESULT);
            } else if (holdErrorBody) {
                respondWithHeldErrorBody(exchange, toolsListStatus);
            } else {
                respond(exchange, toolsListStatus, errorContentType, errorBody);
            }
        });
        server.start();
        StreamableHttpMcpTransport.Builder builder = backend.configure(StreamableHttpMcpTransport.builder()
                .url("http://localhost:" + server.getAddress().getPort() + "/mcp"));
        if (useResponseExecutor) {
            builder.executor(responseExecutor);
        }
        transport = builder.build();
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
        respond(exchange, statusCode, "application/json", body);
    }

    private static void respond(HttpExchange exchange, int statusCode, String contentType, String body)
            throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @AfterEach
    void tearDown() throws IOException {
        errorBodyReleased.countDown();
        if (transport != null) {
            transport.close();
        }
        if (server != null) {
            server.stop(0);
        }
        serverExecutor.shutdownNow();
        responseExecutor.shutdownNow();
    }

    @Test
    void rejected_credential_carries_the_status_code() throws Exception {
        startServer(401);

        transport.sendInitializeRequest(new McpInitializeRequest(0L)).get(5, TimeUnit.SECONDS);
        CompletableFuture<String> response = transport.sendRequest(new McpListToolsRequest(1L, null));

        assertThat(failureOf(response).statusCode()).isEqualTo(401);
    }

    @Test
    void server_error_carries_its_own_status_code() throws Exception {
        startServer(500);

        transport.sendInitializeRequest(new McpInitializeRequest(0L)).get(5, TimeUnit.SECONDS);
        CompletableFuture<String> response = transport.sendRequest(new McpListToolsRequest(1L, null));

        assertThat(failureOf(response).statusCode()).isEqualTo(500);
    }

    @ParameterizedTest
    @CsvSource({"401, JDK", "503, JDK", "503, OKHTTP", "503, APACHE"})
    void error_status_fails_before_the_response_body_is_complete(int status, Backend backend) throws Exception {
        startServer(status, true, backend);

        transport.sendInitializeRequest(new McpInitializeRequest(0L)).get(5, TimeUnit.SECONDS);
        CompletableFuture<String> response = transport.sendRequest(new McpListToolsRequest(1L, null));

        assertThat(failureOf(response).statusCode()).isEqualTo(status);
        assertThat(errorBodyReleased.getCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(Backend.class)
    void expired_session_is_reinitialized_before_the_response_body_is_complete(Backend backend) throws Exception {
        startServer(404, true, backend);

        transport.sendInitializeRequest(new McpInitializeRequest(0L)).get(5, TimeUnit.SECONDS);
        CompletableFuture<String> response = transport.sendRequest(new McpListToolsRequest(1L, null));

        HttpException failure = failureOf(response);
        assertThat(failure.statusCode()).isEqualTo(404);
        assertThat(failure).hasMessage("Session expired again after reinitialization: server returned status code 404");
        assertThat(errorBodyReleased.getCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"JDK, false", "OKHTTP, false", "APACHE, false", "JDK, true"})
    void json_rpc_error_with_a_client_error_status_completes_the_request(Backend backend, boolean onExecutor)
            throws Exception {
        // e.g. a request whose headers do not match its body (2026-07-28)
        errorBody = "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32020,\"message\":\"Header mismatch\"}}";
        useResponseExecutor = onExecutor;
        startServer(400, false, backend);

        CompletableFuture<String> response = listToolsWithModernProtocol();

        assertThat(McpJson.toMap(response.get(5, TimeUnit.SECONDS)))
                .containsEntry("id", 1)
                .containsEntry("error", Map.of("code", -32020, "message", "Header mismatch"));
    }

    @Test
    void json_rpc_error_without_an_id_is_the_answer_to_the_request() throws Exception {
        errorBody = "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32700,\"message\":\"Parse error\"}}";
        startServer(400);

        CompletableFuture<String> response = listToolsWithModernProtocol();

        assertThat(response.get(5, TimeUnit.SECONDS)).isEqualTo(errorBody);
    }

    @Test
    void json_rpc_error_for_another_request_fails_with_the_status_code() throws Exception {
        errorBody = "{\"jsonrpc\":\"2.0\",\"id\":7,\"error\":{\"code\":-32020,\"message\":\"Header mismatch\"}}";
        startServer(400);

        CompletableFuture<String> response = listToolsWithModernProtocol();

        HttpException failure = failureOf(response);
        assertThat(failure.statusCode()).isEqualTo(400);
        assertThat(failure).hasMessage("Unexpected status code: 400");
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "application/json | {\"message\":\"Bad request\"}",
                "text/html | <html>Bad request</html>",
                // a JSON-RPC error is only read from a JSON body
                "text/plain | {\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32020,\"message\":\"Header mismatch\"}}"
            })
    void client_error_without_a_json_rpc_error_fails_with_the_status_code(String contentType, String body)
            throws Exception {
        errorContentType = contentType;
        errorBody = body;
        startServer(400);

        CompletableFuture<String> response = listToolsWithModernProtocol();

        HttpException failure = failureOf(response);
        assertThat(failure.statusCode()).isEqualTo(400);
        assertThat(failure).hasMessage("Unexpected status code: 400");
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 429})
    void json_rpc_error_with_an_authorization_or_rate_limit_status_fails_with_the_status_code(int status)
            throws Exception {
        errorBody = "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32000,\"message\":\"Rejected\"}}";
        startServer(status);

        CompletableFuture<String> response = listToolsWithModernProtocol();

        HttpException failure = failureOf(response);
        assertThat(failure.statusCode()).isEqualTo(status);
        assertThat(failure).hasMessage("Unexpected status code: " + status);
    }

    @Test
    void json_rpc_error_of_a_legacy_server_fails_with_the_status_code() throws Exception {
        errorBody = NO_VALID_SESSION;
        startServer(400);

        transport.sendInitializeRequest(new McpInitializeRequest(0L)).get(5, TimeUnit.SECONDS);
        CompletableFuture<String> response = transport.sendRequest(new McpListToolsRequest(1L, null));

        HttpException failure = failureOf(response);
        assertThat(failure.statusCode()).isEqualTo(400);
        assertThat(failure).hasMessage("Unexpected status code: 400");
    }

    @Test
    void legacy_health_check_fails_when_the_server_no_longer_knows_the_session() throws Exception {
        try (DefaultMcpClient client = startClient(legacyServer(NO_VALID_SESSION, true))) {
            Throwable failure = catchThrowable(client::checkHealth);

            assertThat(failure).hasRootCauseInstanceOf(HttpException.class);
            assertThat(failure).hasRootCauseMessage("Unexpected status code: 400");
        }
    }

    @Test
    void legacy_initialization_rejected_with_a_json_rpc_error_fails() throws Exception {
        String alreadyInitialized =
                "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32600,\"message\":\"Invalid Request: Server already initialized\"},\"id\":null}";

        Throwable failure = catchThrowable(() -> startClient(legacyServer(alreadyInitialized, false)));

        assertThat(failure).hasRootCauseInstanceOf(HttpException.class);
        assertThat(failure).hasRootCauseMessage("Unexpected status code: 400");
    }

    @Test
    void modern_health_check_fails_when_the_server_answers_with_a_json_rpc_error() throws Exception {
        AtomicInteger discoverRequests = new AtomicInteger();
        String unsupportedVersion =
                "{\"jsonrpc\":\"2.0\",\"id\":%s,\"error\":{\"code\":-32022,\"message\":\"Unsupported protocol version\",\"data\":{\"supported\":[\"2027-01-01\"]}}}";
        HttpHandler server = exchange -> {
            Map<String, Object> request =
                    McpJson.toMap(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            Object id = request.get("id");
            if ("server/discover".equals(request.get("method")) && discoverRequests.getAndIncrement() == 0) {
                respond(
                        exchange,
                        200,
                        "{\"jsonrpc\":\"2.0\",\"id\":" + id
                                + ",\"result\":{\"supportedVersions\":[\"2026-07-28\"],\"capabilities\":{},\"resultType\":\"complete\"}}");
            } else {
                // e.g. the server was redeployed with another protocol version
                respond(exchange, 400, unsupportedVersion.formatted(id));
            }
        };

        try (DefaultMcpClient client = startClient(server)) {
            assertThat(client.isModernProtocol()).isTrue();

            Throwable failure = catchThrowable(client::checkHealth);

            assertThat(failure).isInstanceOf(McpException.class);
            assertThat(((McpException) failure).errorCode()).isEqualTo(-32022);
        }
    }

    @Test
    void unsupported_protocol_version_sent_with_a_client_error_status_is_retried_with_a_supported_version()
            throws Exception {
        List<String> initializedVersions = new CopyOnWriteArrayList<>();
        String unsupportedVersion =
                "{\"jsonrpc\":\"2.0\",\"id\":%s,\"error\":{\"code\":-32022,\"message\":\"Unsupported protocol version\",\"data\":{\"supported\":[\"2024-11-05\"]}}}";

        try (DefaultMcpClient client = startClient(detectingServer(unsupportedVersion, initializedVersions))) {
            assertThat(client.isModernProtocol()).isFalse();
        }

        assertThat(initializedVersions).containsExactly("2024-11-05");
    }

    @Test
    void legacy_server_that_rejects_the_detection_request_with_a_json_rpc_error_is_initialized_as_legacy()
            throws Exception {
        List<String> initializedVersions = new CopyOnWriteArrayList<>();
        try (DefaultMcpClient client = startClient(detectingServer(NO_VALID_SESSION, initializedVersions))) {
            assertThat(client.isModernProtocol()).isFalse();
        }

        assertThat(initializedVersions).containsExactly("2025-11-25");
    }

    @Test
    void successful_request_is_unaffected() throws Exception {
        startServer(200);

        transport.sendInitializeRequest(new McpInitializeRequest(0L)).get(5, TimeUnit.SECONDS);
        CompletableFuture<String> response = transport.sendRequest(new McpListToolsRequest(1L, null));

        assertThat(response.get(5, TimeUnit.SECONDS)).contains("\"tools\":[]");
    }

    private CompletableFuture<String> listToolsWithModernProtocol() {
        transport.setModernProtocol(true);
        return transport.sendRequest(new McpListToolsRequest(1L, null));
    }

    /**
     * A legacy server that rejects {@code server/discover} like a TypeScript SDK server, and answers
     * {@code initialize} with the given rejection, or accepts it and then rejects every request with it.
     */
    private static HttpHandler legacyServer(String rejection, boolean acceptsInitialize) {
        return exchange -> {
            Map<String, Object> request =
                    McpJson.toMap(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            Object method = request.get("method");
            if ("server/discover".equals(method)) {
                respond(exchange, 400, NO_VALID_SESSION);
            } else if ("initialize".equals(method) && acceptsInitialize) {
                exchange.getResponseHeaders().set("Mcp-Session-Id", "session-1");
                respond(exchange, 200, INITIALIZE_RESULT.replace("\"id\":0", "\"id\":" + request.get("id")));
            } else if ("notifications/initialized".equals(method)) {
                exchange.sendResponseHeaders(202, -1);
                exchange.close();
            } else {
                respond(exchange, 400, rejection);
            }
        };
    }

    private DefaultMcpClient startClient(HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.setExecutor(serverExecutor);
        server.createContext("/mcp", handler);
        server.start();
        transport = StreamableHttpMcpTransport.builder()
                .url("http://localhost:" + server.getAddress().getPort() + "/mcp")
                .subsidiaryChannel(false)
                .build();
        return DefaultMcpClient.builder()
                .transport(transport)
                .autoHealthCheck(false)
                .build();
    }

    /**
     * Rejects {@code server/discover} with status 400 and the given JSON-RPC error, in which {@code %s} stands for
     * the id of the request, and accepts a legacy {@code initialize}, recording its protocol version.
     */
    private static HttpHandler detectingServer(String discoverError, List<String> initializedVersions) {
        return exchange -> {
            Map<String, Object> request =
                    McpJson.toMap(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            Object id = request.get("id");
            if ("server/discover".equals(request.get("method"))) {
                respond(exchange, 400, discoverError.formatted(id));
            } else if ("initialize".equals(request.get("method"))) {
                Map<?, ?> params = (Map<?, ?>) request.get("params");
                initializedVersions.add((String) params.get("protocolVersion"));
                respond(
                        exchange,
                        200,
                        "{\"jsonrpc\":\"2.0\",\"id\":" + id
                                + ",\"result\":{\"protocolVersion\":\"" + params.get("protocolVersion")
                                + "\",\"capabilities\":{},\"serverInfo\":{\"name\":\"test-server\",\"version\":\"1.0\"}}}");
            } else {
                exchange.sendResponseHeaders(202, -1);
                exchange.close();
            }
        };
    }

    private static HttpException failureOf(CompletableFuture<String> response) {
        Throwable thrown = catchThrowable(() -> response.get(5, TimeUnit.SECONDS));
        assertThat(thrown).isInstanceOf(ExecutionException.class).hasCauseInstanceOf(HttpException.class);
        return (HttpException) thrown.getCause();
    }

    private void respondWithHeldErrorBody(HttpExchange exchange, int statusCode) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, 0);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write("{\"error\":".getBytes(StandardCharsets.UTF_8));
            out.flush();
            errorBodyReleased.await(10, TimeUnit.SECONDS);
            out.write("\"held\"}".getBytes(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException ignored) {
            // the client may have closed the connection after reading the status
        }
    }
}
