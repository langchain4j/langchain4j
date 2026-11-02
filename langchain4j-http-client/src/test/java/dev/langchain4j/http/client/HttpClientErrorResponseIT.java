package dev.langchain4j.http.client;

import static dev.langchain4j.http.client.HttpMethod.GET;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.http.client.sse.HttpErrorResponseReceived;
import dev.langchain4j.http.client.sse.HttpStreamingEvent;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies how {@link HttpClient#stream(HttpRequest)} reports an error response, for implementations that
 * emit {@link HttpErrorResponseReceived}. Subclass and provide a {@link #client()}.
 * <ul>
 *     <li>The event arrives as soon as the response headers arrive, even if the body never completes.
 *         Cancelling then aborts the request: a bare {@link ServerSocket} observes the client closing the
 *         connection. Implementations that cannot abort an in-flight request override
 *         {@link #cancelAbortsConnection()} to return {@code false}.</li>
 *     <li>A subscriber that ignores the event still gets the {@link HttpException} with the full body.</li>
 *     <li>Without demand, the event is skipped and only the {@link HttpException} is signalled.</li>
 * </ul>
 */
public abstract class HttpClientErrorResponseIT {

    protected abstract HttpClient client();

    /**
     * Whether cancelling the subscription aborts the in-flight request and closes the connection.
     * Defaults to {@code true}; override for clients that cannot abort an in-flight request.
     */
    protected boolean cancelAbortsConnection() {
        return true;
    }

    private ServerSocket serverSocket;
    private ExecutorService serverExecutor;
    private final AtomicReference<Socket> acceptedSocket = new AtomicReference<>();
    private final CountDownLatch connectionClosedByClient = new CountDownLatch(1);

    @BeforeEach
    void beforeEach() throws Exception {
        serverSocket = new ServerSocket(0);
        serverExecutor = Executors.newSingleThreadExecutor();
    }

    @AfterEach
    void afterEach() throws Exception {
        Socket socket = acceptedSocket.get();
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // already closed
            }
        }
        serverExecutor.shutdownNow();
        serverSocket.close();
    }

    @Test
    void should_emit_the_error_response_head_before_a_stalled_body_and_abort_when_cancelled() throws Exception {
        respondOnce("HTTP/1.1 404 Not Found\r\n"
                + "Content-Type: application/json\r\n"
                + "X-Error: session expired\r\n"
                + "Content-Length: 1000\r\n"
                + "\r\n"
                + "{\"error\":");
        RecordingSubscriber subscriber = subscribe(1);

        HttpErrorResponseReceived head = (HttpErrorResponseReceived) subscriber.next();
        assertThat(head.statusCode()).isEqualTo(404);
        assertThat(head.headers().get("x-error")).containsExactly("session expired");

        subscriber.subscription.cancel();

        if (cancelAbortsConnection()) {
            assertThat(connectionClosedByClient.await(5, SECONDS)).isTrue();
        }
        assertThat(subscriber.signals.poll(200, MILLISECONDS)).isNull();
    }

    @Test
    void should_fail_with_the_full_body_when_the_error_response_head_is_ignored() throws Exception {
        String body = "model error\r\nwith details";
        respondOnce(errorResponse(422, body));
        RecordingSubscriber subscriber = subscribe(1);

        assertThat(subscriber.next()).isInstanceOf(HttpErrorResponseReceived.class);
        HttpException failure = (HttpException) subscriber.next();
        assertThat(failure.statusCode()).isEqualTo(422);
        assertThat(failure).hasMessage(body);
    }

    @Test
    void should_only_fail_when_there_is_no_demand() throws Exception {
        respondOnce(errorResponse(403, "denied"));
        RecordingSubscriber subscriber = subscribe(0);

        HttpException failure = (HttpException) subscriber.next();
        assertThat(failure.statusCode()).isEqualTo(403);
        assertThat(failure).hasMessage("denied");
        assertThat(subscriber.signals.poll(200, MILLISECONDS)).isNull();
    }

    private static String errorResponse(int statusCode, String body) {
        return "HTTP/1.1 " + statusCode + " Error\r\n"
                + "Content-Type: text/plain\r\n"
                + "Content-Length: " + body.getBytes(UTF_8).length + "\r\n"
                + "\r\n"
                + body;
    }

    /**
     * Accepts one connection, reads the request head, writes {@code response} and keeps the connection open
     * until the client closes it.
     */
    private void respondOnce(String response) {
        serverExecutor.submit(() -> {
            boolean responded = false;
            try (Socket socket = serverSocket.accept()) {
                acceptedSocket.set(socket);
                InputStream in = socket.getInputStream();
                readRequestHead(in);
                OutputStream out = socket.getOutputStream();
                out.write(response.getBytes(UTF_8));
                out.flush();
                responded = true;
                if (in.read() == -1) {
                    connectionClosedByClient.countDown();
                }
            } catch (IOException e) {
                if (responded) {
                    connectionClosedByClient.countDown(); // connection reset by the client
                }
            }
            return null;
        });
    }

    private static void readRequestHead(InputStream in) throws IOException {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            head.write(b);
            if (head.toString(UTF_8).endsWith("\r\n\r\n")) {
                return;
            }
        }
    }

    private RecordingSubscriber subscribe(long demand) {
        RecordingSubscriber subscriber = new RecordingSubscriber(demand);
        HttpRequest request = HttpRequest.builder()
                .method(GET)
                .url("http://localhost:" + serverSocket.getLocalPort() + "/error")
                .build();
        client().stream(request).subscribe(subscriber);
        return subscriber;
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
            assertThat(next).as("next signal").isNotNull();
            return next;
        }
    }
}
