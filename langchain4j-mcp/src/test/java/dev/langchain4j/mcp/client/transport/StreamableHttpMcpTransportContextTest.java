package dev.langchain4j.mcp.client.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.HttpResponseReceived;
import dev.langchain4j.http.client.sse.HttpStreamingEvent;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.protocol.McpInitializeRequest;
import dev.langchain4j.mcp.protocol.McpListToolsRequest;
import dev.langchain4j.mcp.protocol.McpPingResponse;
import dev.langchain4j.spi.CapturedContext;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Flow;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class StreamableHttpMcpTransportContextTest {

    private static final String URL = "https://mcp.example.test/mcp";
    private static final String INITIALIZE_RESULT =
            "{\"jsonrpc\":\"2.0\",\"id\":0,\"result\":{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{},\"serverInfo\":{\"name\":\"test\",\"version\":\"1\"}}}";

    private final ThreadLocal<String> identity = new ThreadLocal<>();
    private final AtomicInteger captures = new AtomicInteger();
    private final RecordingHttpClient httpClient = new RecordingHttpClient();
    private final Map<Long, CompletableFuture<String>> pendingOperations = new ConcurrentHashMap<>();
    private final AtomicReference<String> toolUpdateIdentity = new AtomicReference<>();
    private StreamableHttpMcpTransport transport;

    @AfterEach
    void tearDown() throws IOException {
        if (transport != null) {
            transport.close();
        }
        identity.remove();
    }

    @Test
    void queued_requests_keep_their_own_context_after_initialization() throws Exception {
        start(false);
        identity.set("connection");
        CompletableFuture<String> initialized = transport.sendInitializeRequest(new McpInitializeRequest(0L));
        Exchange initialization = httpClient.nextExchange();
        assertScope(initialization, "connection");

        identity.set("user-a");
        CompletableFuture<String> first = transport.sendRequest(new McpListToolsRequest(1L, null));
        identity.set("user-b");
        CompletableFuture<String> second = transport.sendRequest(new McpListToolsRequest(2L, null));
        assertThat(httpClient.exchanges).isEmpty();
        assertThat(captures).hasValue(3);

        identity.remove();
        onOtherThread(() -> initialization.respond(200, INITIALIZE_RESULT));

        List<Exchange> followUps = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            followUps.add(httpClient.nextExchange());
        }
        for (Exchange exchange : followUps) {
            String body = exchange.request.body();
            if (body.contains("notifications/initialized")) {
                assertScope(exchange, "connection");
                onOtherThread(() -> exchange.respond(202, null));
            } else if (body.contains("\"id\":1")) {
                assertScope(exchange, "user-a");
                onOtherThread(() -> exchange.respond(200, toolsResult(1)));
            } else {
                assertThat(body).contains("\"id\":2");
                assertScope(exchange, "user-b");
                onOtherThread(() -> exchange.respond(200, toolsResult(2)));
            }
        }

        assertThat(initialized.get(5, TimeUnit.SECONDS)).isEqualTo(INITIALIZE_RESULT);
        assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(toolsResult(1));
        assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo(toolsResult(2));
        assertThat(captures).hasValue(3);
        assertThat(identity.get()).isNull();
    }

    @Test
    void response_handlers_and_synchronous_future_continuations_use_request_context() throws Exception {
        start(false);
        identity.set("user-a");
        CompletableFuture<String> result = transport.sendRequest(new McpListToolsRequest(1L, null));
        AtomicReference<String> continuationIdentity = new AtomicReference<>();
        result.whenComplete((ignored, failure) -> continuationIdentity.set(identity.get()));
        Exchange exchange = httpClient.nextExchange();
        assertScope(exchange, "user-a");

        identity.remove();
        onOtherThread(() -> {
            exchange.open(200, Map.of("Content-Type", List.of("text/event-stream")));
            exchange.body("data: " + toolsResult(1) + "\n\n");
        });

        assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(" " + toolsResult(1));
        assertThat(continuationIdentity).hasValue("user-a");
        onOtherThread(() -> {
            exchange.body("data: {\"jsonrpc\":\"2.0\",\"method\":\"notifications/tools/list_changed\"}\n\n");
            exchange.complete();
        });

        assertThat(toolUpdateIdentity).hasValue("user-a");
        assertThat(captures).hasValue(1);
        assertThat(identity.get()).isNull();
    }

    @Test
    void send_message_captures_context_once_for_the_wire_reply() throws Exception {
        start(false);
        identity.set("user-a");
        transport.sendMessage(new McpPingResponse(7L));
        Exchange exchange = httpClient.nextExchange();
        assertThat(exchange.request.body()).contains("\"id\":7");
        assertScope(exchange, "user-a");
        identity.remove();

        onOtherThread(() -> exchange.respond(202, null));

        assertThat(captures).hasValue(1);
        assertThat(pendingOperations).isEmpty();
        assertThat(identity.get()).isNull();
    }

    @Test
    void reinitialization_uses_connection_context_and_retry_uses_original_request_context() throws Exception {
        start(false);
        identity.set("connection");
        CompletableFuture<String> initialized = transport.sendInitializeRequest(new McpInitializeRequest(0L));
        Exchange initialization = httpClient.nextExchange();
        identity.remove();
        onOtherThread(() -> initialization.respond(200, INITIALIZE_RESULT));
        Exchange notification = httpClient.nextExchange();
        assertScope(notification, "connection");
        onOtherThread(() -> notification.respond(202, null));
        assertThat(initialized.get(5, TimeUnit.SECONDS)).isEqualTo(INITIALIZE_RESULT);

        identity.set("user-a");
        CompletableFuture<String> result = transport.sendRequest(new McpListToolsRequest(1L, null));
        Exchange rejected = httpClient.nextExchange();
        assertScope(rejected, "user-a");
        identity.remove();
        onOtherThread(() -> rejected.fail(new HttpException(404, "Session expired")));

        Exchange reinitialization = httpClient.nextExchange();
        assertThat(reinitialization.request.body()).contains("\"method\":\"initialize\"");
        assertScope(reinitialization, "connection");
        onOtherThread(() -> reinitialization.respond(200, INITIALIZE_RESULT));
        Exchange reinitializationNotification = httpClient.nextExchange();
        assertScope(reinitializationNotification, "connection");
        onOtherThread(() -> reinitializationNotification.respond(202, null));

        Exchange retry = httpClient.nextExchange();
        assertThat(retry.request.body()).contains("\"id\":1");
        assertScope(retry, "user-a");
        onOtherThread(() -> retry.respond(200, toolsResult(1)));

        assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(toolsResult(1));
        assertThat(captures).hasValue(2);
        assertThat(identity.get()).isNull();
    }

    @Test
    void subsidiary_channel_reconnect_uses_connection_context_without_recapture() throws Exception {
        start(true);
        identity.set("connection");
        CompletableFuture<String> initialized = transport.sendInitializeRequest(new McpInitializeRequest(0L));
        Exchange initialization = httpClient.nextExchange();
        identity.remove();
        onOtherThread(() -> initialization.respond(200, INITIALIZE_RESULT));
        Exchange notification = httpClient.nextExchange();
        assertScope(notification, "connection");
        onOtherThread(() -> notification.respond(202, null));

        Exchange channel = httpClient.nextExchange();
        assertThat(channel.request.method().name()).isEqualTo("GET");
        assertScope(channel, "connection");
        onOtherThread(() -> channel.open(200, Map.of("Content-Type", List.of("text/event-stream"))));
        assertThat(initialized.get(5, TimeUnit.SECONDS)).isEqualTo(INITIALIZE_RESULT);
        onOtherThread(() -> {
            channel.body("id: event-7\nretry: 0\n\n");
            channel.complete();
        });

        Exchange reconnect = httpClient.nextExchange();
        assertThat(reconnect.request.method().name()).isEqualTo("GET");
        assertThat(reconnect.request.headers()).containsEntry("Last-Event-ID", List.of("event-7"));
        assertScope(reconnect, "connection");
        assertThat(captures).hasValue(1);
        assertThat(identity.get()).isNull();
    }

    @Test
    void cancelling_a_queued_request_does_not_send_it_or_leak_scope() throws Exception {
        start(false);
        identity.set("connection");
        CompletableFuture<String> initialized = transport.sendInitializeRequest(new McpInitializeRequest(0L));
        Exchange initialization = httpClient.nextExchange();
        identity.set("user-a");
        CompletableFuture<String> queued = transport.sendRequest(new McpListToolsRequest(1L, null));
        identity.remove();

        assertThat(queued.cancel(true)).isTrue();
        assertThat(identity.get()).isNull();
        onOtherThread(() -> initialization.respond(200, INITIALIZE_RESULT));
        Exchange notification = httpClient.nextExchange();
        assertScope(notification, "connection");
        onOtherThread(() -> notification.respond(202, null));

        assertThat(initialized.get(5, TimeUnit.SECONDS)).isEqualTo(INITIALIZE_RESULT);
        assertThat(queued).isCancelled();
        assertThat(httpClient.exchanges).isEmpty();
        assertThat(captures).hasValue(2);
        assertThat(identity.get()).isNull();
    }

    @Test
    void cancelling_an_active_request_cancels_backend_in_request_scope() throws Exception {
        start(false);
        identity.set("user-a");
        CompletableFuture<String> result = transport.sendRequest(new McpListToolsRequest(1L, null));
        Exchange exchange = httpClient.nextExchange();
        identity.remove();

        assertThat(result.cancel(true)).isTrue();

        assertThat(exchange.cancelled).isTrue();
        assertThat(exchange.cancelIdentity).isEqualTo("user-a");
        assertThat(identity.get()).isNull();
        assertThat(captures).hasValue(1);
    }

    @Test
    void capture_failure_or_null_context_fails_without_sending() throws Exception {
        IllegalStateException captureFailure = new IllegalStateException("capture failed");
        start(false, () -> {
            throw captureFailure;
        });
        CompletableFuture<String> failedCapture = transport.sendRequest(new McpListToolsRequest(1L, null));

        assertThatThrownBy(() -> failedCapture.get(5, TimeUnit.SECONDS)).hasCause(captureFailure);
        assertThat(httpClient.exchanges).isEmpty();

        transport.close();
        start(false, () -> null);
        CompletableFuture<String> nullCapture = transport.sendRequest(new McpListToolsRequest(2L, null));

        assertThatThrownBy(() -> nullCapture.get(5, TimeUnit.SECONDS))
                .hasCauseInstanceOf(IllegalArgumentException.class);
        assertThat(httpClient.exchanges).isEmpty();
    }

    @Test
    void scope_failure_before_dispatch_fails_request_without_sending() throws Exception {
        IllegalStateException scopeFailure = new IllegalStateException("scope failed before dispatch");
        start(
                false,
                () -> task -> () -> {
                    throw scopeFailure;
                });
        identity.set("user-a");

        CompletableFuture<String> result = transport.sendRequest(new McpListToolsRequest(1L, null));

        assertThatThrownBy(() -> result.get(5, TimeUnit.SECONDS)).hasCause(scopeFailure);
        assertThat(httpClient.exchanges).isEmpty();
        assertThat(pendingOperations).isEmpty();
        assertThat(identity.get()).isEqualTo("user-a");
    }

    @Test
    void wrapping_failure_before_dispatch_fails_request_without_sending() throws Exception {
        IllegalStateException wrappingFailure = new IllegalStateException("wrapping failed before dispatch");
        start(false, () -> task -> {
            throw wrappingFailure;
        });
        identity.set("user-a");

        CompletableFuture<String> result = transport.sendRequest(new McpListToolsRequest(1L, null));

        assertThatThrownBy(() -> result.get(5, TimeUnit.SECONDS)).hasCause(wrappingFailure);
        assertThat(httpClient.exchanges).isEmpty();
        assertThat(pendingOperations).isEmpty();
        assertThat(identity.get()).isEqualTo("user-a");
    }

    @Test
    void scope_failure_during_response_cancels_backend_and_fails_request() throws Exception {
        AtomicBoolean failScope = new AtomicBoolean();
        IllegalStateException scopeFailure = new IllegalStateException("scope failed");
        start(
                false,
                () -> task -> () -> {
                    if (failScope.get()) {
                        throw scopeFailure;
                    }
                    task.run();
                });
        identity.set("user-a");
        CompletableFuture<String> result = transport.sendRequest(new McpListToolsRequest(1L, null));
        Exchange exchange = httpClient.nextExchange();
        identity.remove();
        failScope.set(true);

        onOtherThread(() -> exchange.respond(200, toolsResult(1)));

        assertThatThrownBy(() -> result.get(5, TimeUnit.SECONDS)).hasCause(scopeFailure);
        assertThat(exchange.cancelled).isTrue();
        assertThat(identity.get()).isNull();
    }

    private void start(boolean subsidiaryChannel) {
        start(subsidiaryChannel, this::capture);
    }

    private void start(boolean subsidiaryChannel, Supplier<CapturedContext> requestContextFactory) {
        transport = StreamableHttpMcpTransport.builder()
                .url(URL)
                .httpClient(httpClient)
                .customHeaders(context -> Map.of("X-Identity", identity.get()))
                .requestContextFactory(requestContextFactory)
                .subsidiaryChannel(subsidiaryChannel)
                .build();
        transport.start(new McpOperationHandler(
                pendingOperations,
                List::of,
                transport,
                null,
                () -> toolUpdateIdentity.set(identity.get()),
                null,
                () -> {},
                null,
                null,
                null,
                null,
                null,
                null));
    }

    private CapturedContext capture() {
        captures.incrementAndGet();
        String captured = identity.get();
        return task -> () -> {
            String previous = identity.get();
            if (captured == null) {
                identity.remove();
            } else {
                identity.set(captured);
            }
            try {
                task.run();
            } finally {
                if (previous == null) {
                    identity.remove();
                } else {
                    identity.set(previous);
                }
            }
        };
    }

    private void assertScope(Exchange exchange, String expected) {
        assertThat(exchange.streamIdentity).isEqualTo(expected);
        assertThat(exchange.subscribeIdentity).isEqualTo(expected);
        assertThat(exchange.request.headers()).containsEntry("X-Identity", List.of(expected));
    }

    private void onOtherThread(Runnable task) throws Exception {
        CompletableFuture.runAsync(() -> {
                    assertThat(identity.get()).isNull();
                    task.run();
                    assertThat(identity.get()).isNull();
                })
                .get(5, TimeUnit.SECONDS);
    }

    private static String toolsResult(long id) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":{\"tools\":[]}}";
    }

    private final class RecordingHttpClient implements HttpClient {

        private final BlockingQueue<Exchange> exchanges = new LinkedBlockingQueue<>();

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
            throw new AssertionError("MCP requests must use the streaming SPI");
        }

        @Override
        public Flow.Publisher<HttpStreamingEvent> stream(HttpRequest request, ServerSentEventParser parser) {
            String streamIdentity = identity.get();
            return subscriber -> {
                Exchange exchange =
                        new Exchange(request, parser.incremental(), subscriber, streamIdentity, identity.get());
                subscriber.onSubscribe(exchange);
                exchanges.add(exchange);
            };
        }
    }

    private final class Exchange implements Flow.Subscription {

        private final HttpRequest request;
        private final ServerSentEventParser.Incremental parser;
        private final Flow.Subscriber<? super HttpStreamingEvent> subscriber;
        private final String streamIdentity;
        private final String subscribeIdentity;
        private volatile boolean cancelled;
        private volatile String cancelIdentity;

        private Exchange(
                HttpRequest request,
                ServerSentEventParser.Incremental parser,
                Flow.Subscriber<? super HttpStreamingEvent> subscriber,
                String streamIdentity,
                String subscribeIdentity) {
            this.request = request;
            this.parser = parser;
            this.subscriber = subscriber;
            this.streamIdentity = streamIdentity;
            this.subscribeIdentity = subscribeIdentity;
        }

        void open(int status, Map<String, List<String>> headers) {
            subscriber.onNext(new HttpResponseReceived(SuccessfulHttpResponse.builder()
                    .statusCode(status)
                    .headers(headers)
                    .build()));
        }

        void body(String body) {
            for (byte value : body.getBytes(StandardCharsets.UTF_8)) {
                parser.feed(ByteBuffer.wrap(new byte[] {value})).forEach(subscriber::onNext);
            }
        }

        void complete() {
            parser.flush().forEach(subscriber::onNext);
            subscriber.onComplete();
        }

        void respond(int status, String body) {
            open(status, body == null ? Map.of() : Map.of("Content-Type", List.of("application/json")));
            if (body != null) {
                body(body);
            }
            complete();
        }

        void fail(Throwable failure) {
            subscriber.onError(failure);
        }

        @Override
        public void request(long n) {}

        @Override
        public void cancel() {
            cancelled = true;
            cancelIdentity = identity.get();
        }
    }
}
