package dev.langchain4j.mcp.client.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.exception.AsyncNotSupportedException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpMethod;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.HttpErrorResponseReceived;
import dev.langchain4j.http.client.sse.HttpResponseReceived;
import dev.langchain4j.http.client.sse.HttpStreamingEvent;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import dev.langchain4j.mcp.client.McpCallContext;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.protocol.McpCallToolRequest;
import dev.langchain4j.mcp.protocol.McpInitializeRequest;
import dev.langchain4j.mcp.protocol.McpListToolsRequest;
import dev.langchain4j.mcp.protocol.McpPingResponse;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class StreamableHttpMcpTransportSpiTest {

    private static final String URL = "https://mcp.example.test/mcp";
    private static final String INITIALIZE_RESULT =
            "{\"jsonrpc\":\"2.0\",\"id\":0,\"result\":{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{},\"serverInfo\":{\"name\":\"test\",\"version\":\"1\"}}}";
    private static final String TOOLS_RESULT = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":[]}}";

    private final RecordingHttpClient httpClient = new RecordingHttpClient();
    private final HttpClientBuilder httpClientBuilder = mock(HttpClientBuilder.class, RETURNS_SELF);
    private final Map<Long, CompletableFuture<String>> pendingOperations = new ConcurrentHashMap<>();
    private StreamableHttpMcpTransport transport;

    @AfterEach
    void closeTransport() throws IOException {
        if (transport != null) {
            transport.close();
        }
    }

    @Test
    void should_use_supplied_client_for_initialization_notifications_and_json_requests() throws Exception {
        start(builder().customHeaders(Map.of("Authorization", "Bearer user-token")));

        CompletableFuture<String> initialized = transport.sendInitializeRequest(new McpInitializeRequest(0L));
        Exchange initialization = httpClient.nextExchange();
        assertThat(initialization.request.method()).isEqualTo(HttpMethod.POST);
        assertThat(initialization.request.url()).isEqualTo(URL);
        assertThat(initialization.request.headers()).containsEntry("Authorization", List.of("Bearer user-token"));
        assertThat(initialization.request.headers())
                .containsEntry("Accept", List.of("application/json,text/event-stream"));
        assertThat(initialization.request.headers()).doesNotContainKey("Mcp-Session-Id");
        initialization.open(
                200,
                Map.of("content-type", List.of("application/json"), "mcp-session-id", List.of("session-from-spi")));
        initialization.body(INITIALIZE_RESULT);
        initialization.complete();

        Exchange notification = httpClient.nextExchange();
        assertThat(notification.request.body()).contains("notifications/initialized");
        assertThat(notification.request.headers()).containsEntry("Mcp-Session-Id", List.of("session-from-spi"));
        notification.open(202, Map.of());
        notification.complete();

        assertThat(initialized.get(5, TimeUnit.SECONDS)).isEqualTo(INITIALIZE_RESULT);
        assertThat(transport.getMcpSessionId()).isEqualTo("session-from-spi");

        CompletableFuture<String> result = transport.sendRequest(new McpListToolsRequest(1L, null));
        Exchange tools = httpClient.nextExchange();
        assertThat(tools.request.headers()).containsEntry("Mcp-Session-Id", List.of("session-from-spi"));
        tools.open(200, Map.of("Content-Type", List.of("application/json")));
        tools.body(TOOLS_RESULT);
        tools.complete();
        assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(TOOLS_RESULT);
        verify(httpClientBuilder).build();
    }

    @Test
    void should_decode_fragmented_utf8_and_crlf_in_sse_response() throws Exception {
        start(builder());
        CompletableFuture<String> result = transport.sendRequest(new McpListToolsRequest(1L, null));
        Exchange exchange = httpClient.nextExchange();
        exchange.open(200, Map.of("Content-Type", List.of("text/event-stream; charset=utf-8")));
        String response = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"name\":\"Příliš 🦙\"}}";
        exchange.body(": heartbeat\r\nevent: message\r\ndata: " + response + "\r\n\r\n");

        assertThat(McpJson.parse(result.get(5, TimeUnit.SECONDS))).isEqualTo(McpJson.parse(response));
        transport.close();
        assertThat(exchange.cancelled).isTrue();
    }

    @Test
    void should_decode_multiline_json_without_a_trailing_newline() throws Exception {
        start(builder());
        CompletableFuture<String> result = transport.sendRequest(new McpListToolsRequest(1L, null));
        Exchange exchange = httpClient.nextExchange();
        exchange.open(200, Map.of("Content-Type", List.of("application/json")));
        String response = "{\r\n\"jsonrpc\":\"2.0\",\n\"id\":1,\r\"result\":{\"name\":\"Příliš 🦙\"}\n}";
        exchange.body(response);
        exchange.complete();

        assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(response);
    }

    @Test
    void should_cancel_request_before_headers_and_ignore_in_flight_events() throws Exception {
        transport = builder().build();
        McpOperationHandler handler = mock(McpOperationHandler.class);
        transport.start(handler);
        CompletableFuture<String> result = transport.sendRequest(new McpListToolsRequest(1L, null));
        Exchange exchange = httpClient.nextExchange();

        assertThat(result.cancel(true)).isTrue();
        assertThat(exchange.cancelled).isTrue();
        exchange.open(200, Map.of("Content-Type", List.of("text/event-stream")));
        exchange.body("data: " + TOOLS_RESULT + "\n\n");
        exchange.complete();

        verify(handler, never()).onMessage(anyString());
    }

    @Test
    void should_cancel_active_request_on_close_without_closing_supplied_client() throws Exception {
        transport = StreamableHttpMcpTransport.builder()
                .url(URL)
                .httpClient(httpClient)
                .build();
        McpOperationHandler handler = mock(McpOperationHandler.class);
        transport.start(handler);
        transport.sendRequest(new McpListToolsRequest(1L, null));
        Exchange exchange = httpClient.nextExchange();
        exchange.open(200, Map.of("Content-Type", List.of("text/event-stream")));

        transport.close();
        assertThat(exchange.cancelled).isTrue();
        assertThat(httpClient.closed).isFalse();
        exchange.body("data: " + TOOLS_RESULT + "\n\n");
        exchange.complete();

        verify(handler, never()).onMessage(anyString());
    }

    @Test
    void should_cancel_active_requests_before_closing_client_created_by_supplied_builder() throws Exception {
        start(builder());
        CompletableFuture<String> result = transport.sendRequest(new McpListToolsRequest(1L, null));
        Exchange exchange = httpClient.nextExchange();
        exchange.open(200, Map.of("Content-Type", List.of("text/event-stream")));
        httpClient.onClose = () -> assertThat(exchange.cancelled).isTrue();

        transport.close();
        transport.close();

        assertThat(result).isCancelled();
        assertThat(httpClient.closed).isTrue();
        assertThat(httpClient.closeCount).isEqualTo(1);
    }

    @Test
    void should_preserve_http_errors_from_supplied_client() throws Exception {
        start(builder());
        CompletableFuture<String> result = transport.sendRequest(new McpListToolsRequest(1L, null));
        HttpException failure = new HttpException(401, "Token expired");
        httpClient.nextExchange().subscriber.onError(failure);

        assertThatThrownBy(() -> result.get(5, TimeUnit.SECONDS)).hasCause(failure);
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 503})
    void should_fail_as_soon_as_error_response_headers_arrive(int status) throws Exception {
        start(builder());
        CompletableFuture<String> result = transport.sendRequest(new McpListToolsRequest(1L, null));
        Exchange exchange = httpClient.nextExchange();

        exchange.errorHead(status);

        ExecutionException failure = assertThrows(ExecutionException.class, () -> result.get(5, TimeUnit.SECONDS));
        assertThat(failure.getCause()).isInstanceOf(HttpException.class);
        assertThat(((HttpException) failure.getCause()).statusCode()).isEqualTo(status);
        assertThat(exchange.cancelled).isTrue();
    }

    @Test
    void should_reinitialize_and_retry_after_404_headers_without_waiting_for_error_body() throws Exception {
        start(builder());
        CompletableFuture<String> initialized = transport.sendInitializeRequest(new McpInitializeRequest(0L));
        Exchange initialization = httpClient.nextExchange();
        initialization.open(200, Map.of("Content-Type", List.of("application/json")));
        initialization.body(INITIALIZE_RESULT);
        initialization.complete();
        Exchange notification = httpClient.nextExchange();
        notification.open(202, Map.of());
        notification.complete();
        assertThat(initialized.get(5, TimeUnit.SECONDS)).isEqualTo(INITIALIZE_RESULT);

        CompletableFuture<String> result = transport.sendRequest(new McpListToolsRequest(1L, null));
        Exchange expired = httpClient.nextExchange();
        expired.errorHead(404);
        assertThat(expired.cancelled).isTrue();

        Exchange reinitialization = httpClient.nextExchange();
        assertThat(reinitialization.request.body()).contains("\"method\":\"initialize\"");
        reinitialization.open(200, Map.of("Content-Type", List.of("application/json")));
        reinitialization.body(INITIALIZE_RESULT);
        reinitialization.complete();
        Exchange reinitializationNotification = httpClient.nextExchange();
        reinitializationNotification.open(202, Map.of());
        reinitializationNotification.complete();
        Exchange retry = httpClient.nextExchange();
        assertThat(retry.request.body()).contains("\"id\":1");
        retry.open(200, Map.of("Content-Type", List.of("application/json")));
        retry.body(TOOLS_RESULT);
        retry.complete();

        assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(TOOLS_RESULT);
    }

    @Test
    void server_reply_with_colliding_id_should_not_replace_pending_client_request() throws Exception {
        start(builder());
        CompletableFuture<String> clientRequest = transport.sendRequest(new McpListToolsRequest(1L, null));
        Exchange clientExchange = httpClient.nextExchange();
        assertThat(pendingOperations).containsEntry(1L, clientRequest);

        transport.sendMessage(new McpPingResponse(1L));
        Exchange serverReply = httpClient.nextExchange();
        assertThat(serverReply.request.body()).contains("\"id\":1");
        assertThat(pendingOperations).containsEntry(1L, clientRequest);
        serverReply.open(202, Map.of());
        serverReply.complete();

        assertThat(pendingOperations).containsEntry(1L, clientRequest);
        assertThat(clientRequest).isNotDone();
        clientExchange.open(200, Map.of("Content-Type", List.of("application/json")));
        clientExchange.body(TOOLS_RESULT);
        clientExchange.complete();
        assertThat(clientRequest.get(5, TimeUnit.SECONDS)).isEqualTo(TOOLS_RESULT);
        assertThat(pendingOperations).isEmpty();
    }

    @Test
    void retried_server_reply_with_colliding_id_should_not_replace_pending_client_request() throws Exception {
        start(builder());
        CompletableFuture<String> initialized = transport.sendInitializeRequest(new McpInitializeRequest(0L));
        Exchange initialization = httpClient.nextExchange();
        initialization.open(200, Map.of("Content-Type", List.of("application/json")));
        initialization.body(INITIALIZE_RESULT);
        initialization.complete();
        Exchange notification = httpClient.nextExchange();
        notification.open(202, Map.of());
        notification.complete();
        assertThat(initialized.get(5, TimeUnit.SECONDS)).isEqualTo(INITIALIZE_RESULT);

        CompletableFuture<String> clientRequest = transport.sendRequest(new McpListToolsRequest(1L, null));
        Exchange clientExchange = httpClient.nextExchange();
        assertThat(pendingOperations).containsEntry(1L, clientRequest);

        transport.sendMessage(new McpPingResponse(1L));
        Exchange rejectedReply = httpClient.nextExchange();
        assertThat(rejectedReply.request.body()).contains("\"id\":1");
        assertThat(pendingOperations).containsEntry(1L, clientRequest);
        rejectedReply.subscriber.onError(new HttpException(404, "Session expired"));

        Exchange reinitialization = httpClient.nextExchange();
        assertThat(reinitialization.request.body()).contains("\"method\":\"initialize\"");
        reinitialization.open(200, Map.of("Content-Type", List.of("application/json")));
        reinitialization.body(INITIALIZE_RESULT);
        reinitialization.complete();
        Exchange reinitializationNotification = httpClient.nextExchange();
        reinitializationNotification.open(202, Map.of());
        reinitializationNotification.complete();
        assertThat(pendingOperations).containsEntry(1L, clientRequest);

        Exchange retriedReply = httpClient.nextExchange();
        assertThat(retriedReply.request.body()).contains("\"id\":1");
        assertThat(pendingOperations).containsEntry(1L, clientRequest);
        retriedReply.open(202, Map.of());
        retriedReply.complete();

        assertThat(pendingOperations).containsEntry(1L, clientRequest);
        assertThat(clientRequest).isNotDone();
        clientExchange.open(200, Map.of("Content-Type", List.of("application/json")));
        clientExchange.body(TOOLS_RESULT);
        clientExchange.complete();
        assertThat(clientRequest.get(5, TimeUnit.SECONDS)).isEqualTo(TOOLS_RESULT);
        assertThat(pendingOperations).isEmpty();
    }

    @Test
    void should_send_modern_protocol_headers_and_not_reinitialize_on_404() throws Exception {
        start(builder());
        transport.setModernProtocol(true);
        transport.setProtocolVersion("2026-07-28");
        CompletableFuture<String> result = transport.sendRequest(
                new McpCallContext(null, new McpCallToolRequest(1L, "weather", Map.of()), Map.of("region", "Prague")));
        Exchange exchange = httpClient.nextExchange();

        assertThat(exchange.request.method()).isEqualTo(HttpMethod.POST);
        assertThat(exchange.request.headers())
                .containsEntry("MCP-Protocol-Version", List.of("2026-07-28"))
                .containsEntry("Mcp-Method", List.of("tools/call"))
                .containsEntry("Mcp-Name", List.of("weather"))
                .containsEntry("Mcp-Param-region", List.of("Prague"))
                .doesNotContainKey("Mcp-Session-Id");
        assertThat(exchange.request.body()).contains("\"method\":\"tools/call\"");

        HttpException failure = new HttpException(404, "Unknown tool");
        exchange.subscriber.onError(failure);
        assertThatThrownBy(() -> result.get(5, TimeUnit.SECONDS)).hasCause(failure);
        assertThat(httpClient.exchanges).isEmpty();
    }

    @Test
    void should_keep_concurrent_response_parsers_and_correlation_separate() throws Exception {
        start(builder());
        CompletableFuture<String> first = transport.sendRequest(new McpListToolsRequest(1L, null));
        Exchange firstExchange = httpClient.nextExchange();
        firstExchange.open(200, Map.of("Content-Type", List.of("application/json")));
        firstExchange.body("{\"jsonrpc\":\"2.0\",\"id\":1,");

        CompletableFuture<String> second = transport.sendRequest(new McpListToolsRequest(2L, null));
        Exchange secondExchange = httpClient.nextExchange();
        secondExchange.open(200, Map.of("Content-Type", List.of("text/event-stream")));
        String secondResponse = "{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"tools\":[]}}";
        secondExchange.body("data:" + secondResponse + "\n\n");
        secondExchange.complete();

        assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo(secondResponse);
        assertThat(first).isNotDone();
        firstExchange.body("\"result\":{\"tools\":[]}}");
        firstExchange.complete();
        assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(TOOLS_RESULT);
    }

    @Test
    void should_start_queued_request_after_initialization_and_forward_cancellation() throws Exception {
        start(builder());
        CompletableFuture<String> initialized = transport.sendInitializeRequest(new McpInitializeRequest(0L));
        Exchange initialization = httpClient.nextExchange();
        CompletableFuture<String> queued = assertTimeoutPreemptively(
                Duration.ofSeconds(5), () -> transport.sendRequest(new McpListToolsRequest(1L, null)));
        assertThat(httpClient.exchanges).isEmpty();

        initialization.open(200, Map.of("Content-Type", List.of("application/json")));
        initialization.body(INITIALIZE_RESULT);
        assertTimeoutPreemptively(Duration.ofSeconds(5), initialization::complete);

        Exchange tools = null;
        for (int i = 0; i < 2; i++) {
            Exchange exchange = httpClient.nextExchange();
            String method =
                    McpJson.parse(exchange.request.body()).path("method").asText();
            if ("tools/list".equals(method)) {
                tools = exchange;
            } else {
                assertThat(method).isEqualTo("notifications/initialized");
                exchange.open(202, Map.of());
                exchange.complete();
            }
        }
        assertThat(initialized.get(5, TimeUnit.SECONDS)).isEqualTo(INITIALIZE_RESULT);
        assertThat(tools).isNotNull();
        assertThat(queued).isNotDone();
        assertThat(queued.cancel(true)).isTrue();
        assertThat(tools.cancelled).isTrue();
    }

    @Test
    void should_capture_each_queued_requests_headers_from_its_calling_thread() throws Exception {
        ThreadLocal<String> user = new ThreadLocal<>();
        AtomicInteger supplierCalls = new AtomicInteger();
        start(builder().customHeaders(context -> {
            supplierCalls.incrementAndGet();
            return Map.of("Authorization", "Bearer " + user.get());
        }));
        transport.setMcpSessionId("previous-session");
        CompletableFuture<String> initialized = transport.sendInitializeRequest(new McpInitializeRequest(0L));
        Exchange initialization = httpClient.nextExchange();
        CompletableFuture<String> first;
        CompletableFuture<String> second;
        try {
            user.set("user-a");
            first = transport.sendRequest(new McpListToolsRequest(1L, null));
            user.set("user-b");
            second = transport.sendRequest(new McpListToolsRequest(2L, null));
        } finally {
            user.remove();
        }
        assertThat(httpClient.exchanges).isEmpty();

        CompletableFuture.runAsync(() -> {
                    assertThat(user.get()).isNull();
                    initialization.open(
                            200,
                            Map.of(
                                    "Content-Type",
                                    List.of("application/json"),
                                    "Mcp-Session-Id",
                                    List.of("new-session")));
                    initialization.body(INITIALIZE_RESULT);
                    initialization.complete();
                })
                .get(5, TimeUnit.SECONDS);

        for (int i = 0; i < 3; i++) {
            Exchange exchange = httpClient.nextExchange();
            assertThat(exchange.request.headers()).containsEntry("Mcp-Session-Id", List.of("new-session"));
            var message = McpJson.parse(exchange.request.body());
            if ("notifications/initialized".equals(message.path("method").asText())) {
                exchange.open(202, Map.of());
            } else {
                long id = message.path("id").asLong();
                assertThat(id).isIn(1L, 2L);
                assertThat(exchange.request.headers())
                        .containsEntry("Authorization", List.of(id == 1L ? "Bearer user-a" : "Bearer user-b"));
                exchange.open(200, Map.of("Content-Type", List.of("application/json")));
                exchange.body("{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":{\"tools\":[]}}");
            }
            exchange.complete();
        }
        assertThat(initialized.get(5, TimeUnit.SECONDS)).isEqualTo(INITIALIZE_RESULT);
        assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(TOOLS_RESULT);
        assertThat(McpJson.parse(second.get(5, TimeUnit.SECONDS)).path("id").asLong())
                .isEqualTo(2L);
        assertThat(supplierCalls).hasValue(4);
    }

    @Test
    void should_snapshot_mutable_headers_before_waiting_for_initialization() throws Exception {
        Map<String, String> headers = new HashMap<>(Map.of("Authorization", "Bearer original"));
        start(builder().customHeaders(context -> headers));
        CompletableFuture<String> initialized = transport.sendInitializeRequest(new McpInitializeRequest(0L));
        Exchange initialization = httpClient.nextExchange();
        CompletableFuture<String> queued = transport.sendRequest(new McpListToolsRequest(1L, null));
        headers.put("Authorization", "Bearer changed");
        initialization.open(200, Map.of("Content-Type", List.of("application/json")));
        initialization.body(INITIALIZE_RESULT);
        initialization.complete();

        for (int i = 0; i < 2; i++) {
            Exchange exchange = httpClient.nextExchange();
            if ("notifications/initialized"
                    .equals(McpJson.parse(exchange.request.body())
                            .path("method")
                            .asText())) {
                exchange.open(202, Map.of());
            } else {
                assertThat(exchange.request.headers()).containsEntry("Authorization", List.of("Bearer original"));
                exchange.open(200, Map.of("Content-Type", List.of("application/json")));
                exchange.body(TOOLS_RESULT);
            }
            exchange.complete();
        }
        assertThat(initialized.get(5, TimeUnit.SECONDS)).isEqualTo(INITIALIZE_RESULT);
        assertThat(queued.get(5, TimeUnit.SECONDS)).isEqualTo(TOOLS_RESULT);
    }

    @Test
    void should_not_start_queued_request_cancelled_before_initialization_completes() throws Exception {
        start(builder());
        CompletableFuture<String> initialized = transport.sendInitializeRequest(new McpInitializeRequest(0L));
        Exchange initialization = httpClient.nextExchange();
        CompletableFuture<String> queued = assertTimeoutPreemptively(
                Duration.ofSeconds(5), () -> transport.sendRequest(new McpListToolsRequest(1L, null)));
        assertThat(queued.cancel(true)).isTrue();

        initialization.open(200, Map.of("Content-Type", List.of("application/json")));
        initialization.body(INITIALIZE_RESULT);
        initialization.complete();
        Exchange notification = httpClient.nextExchange();
        assertThat(McpJson.parse(notification.request.body()).path("method").asText())
                .isEqualTo("notifications/initialized");
        notification.open(202, Map.of());
        notification.complete();

        assertThat(initialized.get(5, TimeUnit.SECONDS)).isEqualTo(INITIALIZE_RESULT);
        assertThat(queued).isCancelled();
        assertThat(httpClient.exchanges).isEmpty();
    }

    @Test
    void should_propagate_initialization_failure_to_queued_request() throws Exception {
        start(builder());
        CompletableFuture<String> initialized = transport.sendInitializeRequest(new McpInitializeRequest(0L));
        Exchange initialization = httpClient.nextExchange();
        CompletableFuture<String> queued = assertTimeoutPreemptively(
                Duration.ofSeconds(5), () -> transport.sendRequest(new McpListToolsRequest(1L, null)));
        HttpException failure = new HttpException(503, "Initialization unavailable");

        initialization.subscriber.onError(failure);

        assertThatThrownBy(() -> initialized.get(5, TimeUnit.SECONDS)).hasCause(failure);
        assertThatThrownBy(() -> queued.get(5, TimeUnit.SECONDS)).hasCause(failure);
        assertThat(httpClient.exchanges).isEmpty();
    }

    @Test
    void should_fail_without_hanging_when_supplied_client_does_not_support_streaming() {
        StreamableHttpMcpTransport.Builder builder = builder();
        when(httpClientBuilder.build()).thenReturn(new HttpClient() {
            @Override
            public SuccessfulHttpResponse execute(HttpRequest request) {
                throw new AssertionError("Must not fall back to a synchronous request");
            }

            @Override
            public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
                throw new AssertionError("Must not fall back to an uncancellable callback request");
            }
        });
        start(builder);

        CompletableFuture<String> result = transport.sendRequest(new McpListToolsRequest(1L, null));

        assertThatThrownBy(() -> result.get(5, TimeUnit.SECONDS))
                .hasCauseInstanceOf(AsyncNotSupportedException.class)
                .rootCause()
                .hasMessageContaining("stream");
    }

    @Test
    void should_use_supplied_client_for_subsidiary_channel_and_resume_with_event_id() throws Exception {
        start(builder().subsidiaryChannel(true).customHeaders(Map.of("Authorization", "Bearer user-token")));
        CompletableFuture<String> initialized = transport.sendInitializeRequest(new McpInitializeRequest(0L));
        Exchange initialization = httpClient.nextExchange();
        initialization.open(
                200,
                Map.of("Content-Type", List.of("application/json"), "Mcp-Session-Id", List.of("session-from-spi")));
        initialization.body(INITIALIZE_RESULT);
        initialization.complete();
        Exchange notification = httpClient.nextExchange();
        notification.open(202, Map.of());
        notification.complete();

        Exchange channel = httpClient.nextExchange();
        assertThat(channel.request.method()).isEqualTo(HttpMethod.GET);
        assertThat(channel.request.headers()).containsEntry("Accept", List.of("text/event-stream"));
        assertThat(channel.request.headers()).containsEntry("Authorization", List.of("Bearer user-token"));
        assertThat(channel.request.headers()).containsEntry("Mcp-Session-Id", List.of("session-from-spi"));
        channel.open(200, Map.of("Content-Type", List.of("text/event-stream")));
        assertThat(initialized.get(5, TimeUnit.SECONDS)).isEqualTo(INITIALIZE_RESULT);
        channel.body("id: event-7\nretry: 0\n\n");
        channel.complete();

        Exchange reconnect = httpClient.nextExchange();
        assertThat(reconnect.request.method()).isEqualTo(HttpMethod.GET);
        assertThat(reconnect.request.headers()).containsEntry("Last-Event-ID", List.of("event-7"));
        assertThat(reconnect.request.headers()).containsEntry("Mcp-Session-Id", List.of("session-from-spi"));
        transport.close();
        assertThat(reconnect.cancelled).isTrue();
    }

    @Test
    void should_apply_explicit_connection_timeout_without_changing_read_timeout() throws Exception {
        when(httpClientBuilder.connectTimeout()).thenReturn(Duration.ofSeconds(3));
        start(builder().timeout(Duration.ofSeconds(13)));

        verify(httpClientBuilder).connectTimeout(Duration.ofSeconds(13));
        verify(httpClientBuilder, never()).readTimeout(any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {3})
    void should_preserve_supplied_builder_timeout(Long seconds) {
        when(httpClientBuilder.connectTimeout()).thenReturn(seconds == null ? null : Duration.ofSeconds(seconds));
        start(builder());

        verify(httpClientBuilder, never()).connectTimeout(any());
        verify(httpClientBuilder, never()).readTimeout(any());
    }

    @Test
    void should_reject_jdk_specific_transport_options_with_supplied_builder() throws Exception {
        assertThatThrownBy(() -> builder().sslContext(SSLContext.getDefault()).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("httpClientBuilder");
        assertThatThrownBy(() -> builder().setHttpVersion1_1().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("httpClientBuilder");
        assertThatThrownBy(() -> builder().followRedirects(true).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("httpClientBuilder");
    }

    @Test
    void should_reject_configuring_both_http_client_and_builder() {
        assertThatThrownBy(() -> builder().httpClient(httpClient).build()).isInstanceOf(IllegalArgumentException.class);
        verify(httpClientBuilder, never()).build();
    }

    @Test
    void should_reject_transport_connection_settings_with_supplied_client() {
        assertThatThrownBy(() -> StreamableHttpMcpTransport.builder()
                        .url(URL)
                        .httpClient(httpClient)
                        .timeout(Duration.ofSeconds(5))
                        .build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StreamableHttpMcpTransport.builder()
                        .url(URL)
                        .httpClient(httpClient)
                        .sslContext(SSLContext.getDefault())
                        .build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StreamableHttpMcpTransport.builder()
                        .url(URL)
                        .httpClient(httpClient)
                        .setHttpVersion1_1()
                        .build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StreamableHttpMcpTransport.builder()
                        .url(URL)
                        .httpClient(httpClient)
                        .followRedirects(true)
                        .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_reject_null_supplied_client() {
        assertThatThrownBy(() -> StreamableHttpMcpTransport.builder().httpClient(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("httpClient");
    }

    private StreamableHttpMcpTransport.Builder builder() {
        when(httpClientBuilder.build()).thenReturn(httpClient);
        return StreamableHttpMcpTransport.builder().url(URL).httpClientBuilder(httpClientBuilder);
    }

    private void start(StreamableHttpMcpTransport.Builder builder) {
        transport = builder.build();
        transport.start(new McpOperationHandler(
                pendingOperations,
                List::of,
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

    private static class RecordingHttpClient implements HttpClient, AutoCloseable {

        private final BlockingQueue<Exchange> exchanges = new LinkedBlockingQueue<>();
        private boolean closed;
        private int closeCount;
        private Runnable onClose = () -> {};

        Exchange nextExchange() throws InterruptedException {
            Exchange exchange = exchanges.poll(5, TimeUnit.SECONDS);
            assertThat(exchange).as("request sent through supplied HTTP client").isNotNull();
            return exchange;
        }

        @Override
        public SuccessfulHttpResponse execute(HttpRequest request) {
            throw new AssertionError("MCP requests must use the streaming SPI");
        }

        @Override
        public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
            throw new AssertionError("MCP requests must use the cancellable streaming SPI");
        }

        @Override
        public Flow.Publisher<HttpStreamingEvent> stream(HttpRequest request, ServerSentEventParser parser) {
            return subscriber -> {
                Exchange exchange = new Exchange(request, parser.incremental(), subscriber);
                subscriber.onSubscribe(exchange);
                exchanges.add(exchange);
            };
        }

        @Override
        public void close() {
            onClose.run();
            closeCount++;
            closed = true;
        }
    }

    private static class Exchange implements Flow.Subscription {

        private final HttpRequest request;
        private final ServerSentEventParser.Incremental parser;
        private final Flow.Subscriber<? super HttpStreamingEvent> subscriber;
        private volatile boolean cancelled;

        private Exchange(
                HttpRequest request,
                ServerSentEventParser.Incremental parser,
                Flow.Subscriber<? super HttpStreamingEvent> subscriber) {
            this.request = request;
            this.parser = parser;
            this.subscriber = subscriber;
        }

        void open(int status, Map<String, List<String>> headers) {
            subscriber.onNext(new HttpResponseReceived(SuccessfulHttpResponse.builder()
                    .statusCode(status)
                    .headers(headers)
                    .build()));
        }

        void errorHead(int status) {
            subscriber.onNext(new HttpErrorResponseReceived(status, Map.of()));
        }

        void body(String body) {
            // Deliberately split every byte, including CRLF and UTF-8 characters. Also allow events
            // after cancellation to model publisher callbacks already in flight when cancellation wins.
            for (byte value : body.getBytes(StandardCharsets.UTF_8)) {
                parser.feed(ByteBuffer.wrap(new byte[] {value})).forEach(subscriber::onNext);
            }
        }

        void complete() {
            parser.flush().forEach(subscriber::onNext);
            subscriber.onComplete();
        }

        @Override
        public void request(long n) {}

        @Override
        public void cancel() {
            cancelled = true;
        }
    }
}
