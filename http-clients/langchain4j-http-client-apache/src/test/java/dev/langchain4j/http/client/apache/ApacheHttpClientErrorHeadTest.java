package dev.langchain4j.http.client.apache;

import static dev.langchain4j.http.client.HttpMethod.GET;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.sse.DefaultServerSentEventParser;
import dev.langchain4j.http.client.sse.HttpErrorResponseReceived;
import dev.langchain4j.http.client.sse.HttpStreamingEvent;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApacheHttpClientErrorHeadTest {

    private HttpServer server;
    private final ApacheHttpClient client = ApacheHttpClient.builder().build();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void error_head_can_cancel_without_waiting_for_a_stalled_body() throws Exception {
        CountDownLatch releaseBody = new CountDownLatch(1);
        server.createContext("/error", exchange -> {
            exchange.getResponseHeaders().set("X-Error", "unauthorized");
            exchange.sendResponseHeaders(401, 0);
            try (var output = exchange.getResponseBody()) {
                output.write('x');
                output.flush();
                try {
                    releaseBody.await(10, SECONDS);
                    output.write('y');
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (IOException ignored) {
                    // Cancellation may abort the response before the server sends the tail.
                }
            }
        });
        server.start();
        RecordingSubscriber subscriber = subscribe(1);
        try {
            Object first = subscriber.next();
            assertThat(first).isInstanceOf(HttpErrorResponseReceived.class);
            HttpErrorResponseReceived head = (HttpErrorResponseReceived) first;
            assertThat(head.statusCode()).isEqualTo(401);
            assertThat(head.headers().get("x-error")).containsExactly("unauthorized");

            CompletableFuture.runAsync(subscriber::cancel).get(3, SECONDS);
            assertThat(subscriber.signals.poll(150, java.util.concurrent.TimeUnit.MILLISECONDS))
                    .isNull();
        } finally {
            releaseBody.countDown();
        }
    }

    @Test
    void ignoring_error_head_preserves_complete_error_body() throws Exception {
        String body = "model error\r\nwith details";
        server.createContext("/error", exchange -> respond(exchange, 422, body));
        server.start();
        RecordingSubscriber subscriber = subscribe(1);

        assertThat(subscriber.next()).isInstanceOf(HttpErrorResponseReceived.class);
        Object terminal = subscriber.next();
        assertThat(terminal).isInstanceOf(HttpException.class);
        assertThat(((HttpException) terminal).statusCode()).isEqualTo(422);
        assertThat(((HttpException) terminal).getMessage()).isEqualTo(body);
    }

    @Test
    void no_demand_skips_advisory_head_and_still_delivers_terminal_error() throws Exception {
        server.createContext("/error", exchange -> respond(exchange, 403, "denied"));
        server.start();
        RecordingSubscriber subscriber = subscribe(0);

        Object first = subscriber.next();
        assertThat(first).isInstanceOf(HttpException.class);
        assertThat(((HttpException) first).statusCode()).isEqualTo(403);
        assertThat(((HttpException) first).getMessage()).isEqualTo("denied");
        assertThat(subscriber.signals).isEmpty();
    }

    private RecordingSubscriber subscribe(long demand) {
        RecordingSubscriber subscriber = new RecordingSubscriber(demand);
        client.stream(
                        HttpRequest.builder()
                                .method(GET)
                                .url("http://localhost:" + server.getAddress().getPort() + "/error")
                                .build(),
                        new DefaultServerSentEventParser())
                .subscribe(subscriber);
        return subscriber;
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static final class RecordingSubscriber implements Flow.Subscriber<HttpStreamingEvent> {
        private final BlockingQueue<Object> signals = new LinkedBlockingQueue<>();
        private final long demand;
        private volatile Flow.Subscription subscription;

        private RecordingSubscriber(long demand) {
            this.demand = demand;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            if (demand > 0) {
                subscription.request(demand);
            }
        }

        @Override
        public void onNext(HttpStreamingEvent event) {
            signals.add(event);
        }

        @Override
        public void onError(Throwable error) {
            signals.add(error);
        }

        @Override
        public void onComplete() {
            signals.add("completed");
        }

        private Object next() throws InterruptedException {
            Object next = signals.poll(5, SECONDS);
            assertThat(next).isNotNull();
            return next;
        }

        private void cancel() {
            subscription.cancel();
        }
    }
}
