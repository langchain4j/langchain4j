package dev.langchain4j.http.client.okhttp;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.http.client.HttpMethod;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the streaming path does not consume the response body before the parser reads it,
 * regardless of the {@code Content-Type} the server sends.
 */
class OkHttpClientStreamingTest {

    private static final List<String> LINES = List.of("{\"n\":0}", "{\"n\":1}", "{\"n\":2}");

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void should_deliver_all_events_when_content_type_is_not_event_stream() throws Exception {
        respondWith("application/x-ndjson", String.join("\n", LINES));

        RecordingListener listener = streamFrom("/stream");

        assertThat(listener.errors).isEmpty();
        assertThat(listener.events).containsExactlyElementsOf(LINES);
        assertThat(listener.response.get().body()).isNull();
    }

    @Test
    void should_deliver_all_events_when_content_type_is_event_stream() throws Exception {
        respondWith("text/event-stream", String.join("\n", LINES));

        RecordingListener listener = streamFrom("/stream");

        assertThat(listener.errors).isEmpty();
        assertThat(listener.events).containsExactlyElementsOf(LINES);
        assertThat(listener.response.get().body()).isNull();
    }

    @Test
    void should_deliver_all_events_when_content_type_is_missing() throws Exception {
        respondWith(null, String.join("\n", LINES));

        RecordingListener listener = streamFrom("/stream");

        assertThat(listener.errors).isEmpty();
        assertThat(listener.events).containsExactlyElementsOf(LINES);
        assertThat(listener.response.get().body()).isNull();
    }

    @Test
    void should_notify_listener_about_error_when_response_is_not_successful() throws Exception {
        server.createContext("/stream", exchange -> {
            byte[] bytes = "server is down".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/x-ndjson");
            exchange.sendResponseHeaders(500, bytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(bytes);
            }
        });

        RecordingListener listener = streamFrom("/stream");

        assertThat(listener.events).isEmpty();
        assertThat(listener.response.get()).isNull();
        assertThat(listener.errors).singleElement().isInstanceOfSatisfying(HttpException.class, exception -> {
            assertThat(exception.statusCode()).isEqualTo(500);
            assertThat(exception.getMessage()).isEqualTo("server is down");
        });
    }

    @Test
    void should_read_body_in_non_streaming_path() throws Exception {
        respondWith("application/x-ndjson", String.join("\n", LINES));

        SuccessfulHttpResponse response = OkHttpClient.builder().build().execute(request("/stream"));

        assertThat(response.body()).isEqualTo(String.join("\n", LINES));
    }

    @Test
    void should_not_read_body_in_non_streaming_path_when_content_type_is_event_stream() throws Exception {
        respondWith("text/event-stream", String.join("\n", LINES));

        SuccessfulHttpResponse response = OkHttpClient.builder().build().execute(request("/stream"));

        assertThat(response.body()).isNull();
    }

    private void respondWith(String contentType, String body) {
        server.createContext("/stream", (HttpExchange exchange) -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            if (contentType != null) {
                exchange.getResponseHeaders().add("Content-Type", contentType);
            }
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(bytes);
            }
        });
    }

    private RecordingListener streamFrom(String path) throws InterruptedException {
        RecordingListener listener = new RecordingListener();
        OkHttpClient.builder().build().execute(request(path), new LineParser(), listener);
        assertThat(listener.completed.await(30, TimeUnit.SECONDS)).isTrue();
        return listener;
    }

    private HttpRequest request(String path) {
        return HttpRequest.builder().method(HttpMethod.GET).url(baseUrl + path).build();
    }

    /**
     * Emits one event per line, like the parsers used for newline-delimited JSON streams.
     */
    private static class LineParser implements ServerSentEventParser {

        @Override
        public void parse(InputStream httpResponseBody, ServerSentEventListener listener) {
            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(httpResponseBody, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    listener.onEvent(new ServerSentEvent(null, line));
                }
            } catch (IOException e) {
                listener.onError(e);
            }
        }
    }

    private static class RecordingListener implements ServerSentEventListener {

        final AtomicReference<SuccessfulHttpResponse> response = new AtomicReference<>();
        final List<String> events = new ArrayList<>();
        final List<Throwable> errors = new CopyOnWriteArrayList<>();
        final CountDownLatch completed = new CountDownLatch(1);

        @Override
        public void onOpen(SuccessfulHttpResponse response) {
            this.response.set(response);
        }

        @Override
        public void onEvent(ServerSentEvent event) {
            events.add(event.data());
        }

        @Override
        public void onError(Throwable throwable) {
            errors.add(throwable);
            completed.countDown();
        }

        @Override
        public void onClose() {
            completed.countDown();
        }
    }
}
