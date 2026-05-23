package dev.langchain4j.http.client.jdk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.http.client.HttpMethod;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.DefaultServerSentEventParser;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventContext;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdkHttpClientSseTest {

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void delivers_all_events_through_async_path() {
        server.createContext("/sse", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write("event: message\ndata: hello\n\n".getBytes(StandardCharsets.UTF_8));
                os.flush();
                os.write("data: world\n\n".getBytes(StandardCharsets.UTF_8));
                os.flush();
                os.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
        });

        JdkHttpClient client = JdkHttpClient.builder().build();
        RecordingListener listener = new RecordingListener();

        client.execute(getRequest("/sse"), new DefaultServerSentEventParser(), listener);

        await().atMost(5, TimeUnit.SECONDS).until(() -> listener.closed.get());

        assertThat(listener.events).extracting(ServerSentEvent::data).containsExactly("hello", "world", "[DONE]");
        assertThat(listener.events.get(0).event()).isEqualTo("message");
        assertThat(listener.errors).isEmpty();
        assertThat(listener.response.get()).isNotNull();
        assertThat(listener.response.get().statusCode()).isEqualTo(200);
    }

    @Test
    void surfaces_http_error_status_through_async_path() {
        server.createContext("/error", exchange -> {
            byte[] body = "boom".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        JdkHttpClient client = JdkHttpClient.builder().build();
        RecordingListener listener = new RecordingListener();

        client.execute(getRequest("/error"), new DefaultServerSentEventParser(), listener);

        await().atMost(5, TimeUnit.SECONDS).until(() -> !listener.errors.isEmpty());

        assertThat(listener.events).isEmpty();
        assertThat(listener.errors).hasSize(1);
        assertThat(listener.errors.get(0)).hasMessageContaining("boom");
    }

    @Test
    void cancellation_via_parsing_handle_stops_event_delivery() throws InterruptedException {
        CountDownLatch serverWrote = new CountDownLatch(1);
        CountDownLatch clientReleased = new CountDownLatch(1);

        server.createContext("/long", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write("data: first\n\n".getBytes(StandardCharsets.UTF_8));
                os.flush();
                serverWrote.countDown();
                // Hold the connection open until the test releases us, then write one more event
                // that the listener should never observe (because it cancelled).
                clientReleased.await(5, TimeUnit.SECONDS);
                os.write("data: should-not-see\n\n".getBytes(StandardCharsets.UTF_8));
                os.flush();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        JdkHttpClient client = JdkHttpClient.builder().build();
        CancellingListener listener = new CancellingListener();
        client.execute(getRequest("/long"), new DefaultServerSentEventParser(), listener);

        serverWrote.await(5, TimeUnit.SECONDS);
        await().atMost(5, TimeUnit.SECONDS).until(() -> !listener.events.isEmpty());
        clientReleased.countDown();

        // Allow any in-flight delivery to settle.
        TimeUnit.MILLISECONDS.sleep(300);

        assertThat(listener.events).extracting(ServerSentEvent::data).containsExactly("first");
    }

    @Test
    void custom_parser_falls_back_to_input_stream_path() {
        server.createContext("/sse", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write("data: payload\n\n".getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
        });

        JdkHttpClient client = JdkHttpClient.builder().build();
        RecordingListener listener = new RecordingListener();
        RecordingParser parser = new RecordingParser();

        client.execute(getRequest("/sse"), parser, listener);

        await().atMost(5, TimeUnit.SECONDS).until(() -> parser.parsed.get());
    }

    private HttpRequest getRequest(String path) {
        return HttpRequest.builder().method(HttpMethod.GET).url(baseUrl + path).build();
    }

    private static class RecordingListener implements ServerSentEventListener {
        final List<ServerSentEvent> events = new CopyOnWriteArrayList<>();
        final List<Throwable> errors = new CopyOnWriteArrayList<>();
        final AtomicReference<SuccessfulHttpResponse> response = new AtomicReference<>();
        final java.util.concurrent.atomic.AtomicBoolean closed = new java.util.concurrent.atomic.AtomicBoolean(false);

        @Override
        public void onOpen(SuccessfulHttpResponse r) {
            response.set(r);
        }

        @Override
        public void onEvent(ServerSentEvent event, ServerSentEventContext context) {
            events.add(event);
        }

        @Override
        public void onError(Throwable throwable) {
            errors.add(throwable);
            closed.set(true);
        }

        @Override
        public void onClose() {
            closed.set(true);
        }
    }

    private static class CancellingListener implements ServerSentEventListener {
        final List<ServerSentEvent> events = new CopyOnWriteArrayList<>();

        @Override
        public void onEvent(ServerSentEvent event, ServerSentEventContext context) {
            events.add(event);
            context.parsingHandle().cancel();
        }

        @Override
        public void onError(Throwable throwable) {}
    }

    private static class RecordingParser implements ServerSentEventParser {
        final java.util.concurrent.atomic.AtomicBoolean parsed = new java.util.concurrent.atomic.AtomicBoolean(false);

        @Override
        public void parse(InputStream httpResponseBody, ServerSentEventListener listener) {
            try (InputStream in = httpResponseBody) {
                in.readAllBytes();
                parsed.set(true);
            } catch (IOException ignored) {
            }
        }
    }
}
