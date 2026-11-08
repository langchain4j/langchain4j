package dev.langchain4j.http.client.jdk;

import static dev.langchain4j.http.client.HttpMethod.GET;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.sse.DefaultServerSentEventParser;
import dev.langchain4j.http.client.sse.HttpStreamingEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies that overflowing the streaming back-pressure buffer aborts the in-flight request and closes the TCP
 * connection, rather than draining the socket to EOF. The tube's buffer overflow is a terminal failure (not a
 * cancel), so the client must release the connection via a {@code whenTerminates} hook, not {@code whenCancelled}.
 */
class JdkHttpClientStreamOverflowTest {

    private ServerSocket serverSocket;
    private ExecutorService serverExecutor;
    private final AtomicReference<Socket> acceptedSocket = new AtomicReference<>();

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
    void overflowing_the_stream_buffer_aborts_the_request_and_closes_the_connection() throws Exception {

        CountDownLatch serverAccepted = new CountDownLatch(1);
        CountDownLatch clientDisconnected = new CountDownLatch(1);

        serverExecutor.submit(() -> {
            try {
                Socket socket = serverSocket.accept();
                acceptedSocket.set(socket);
                serverAccepted.countDown();

                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();

                // consume the request headers up to the blank line
                StringBuilder headers = new StringBuilder();
                int b;
                while ((b = in.read()) != -1) {
                    headers.append((char) b);
                    if (headers.length() >= 4 && "\r\n\r\n".contentEquals(headers.subSequence(headers.length() - 4, headers.length()))) {
                        break;
                    }
                }

                out.write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\n\r\n".getBytes(UTF_8));
                out.flush();

                // firehose SSE events until the client aborts the read; the write then fails (broken pipe)
                byte[] event = "data: token\n\n".getBytes(UTF_8);
                while (true) {
                    out.write(event);
                    out.flush();
                }
            } catch (IOException e) {
                // the client closed the connection (the overflow aborted the read) - the expected outcome
                clientDisconnected.countDown();
            }
        });

        JdkHttpClient client = JdkHttpClient.builder().streamingBufferSize(1).build();
        HttpRequest request = HttpRequest.builder()
                .method(GET)
                .url("http://localhost:" + serverSocket.getLocalPort() + "/stream")
                .build();

        // A subscriber that never requests anything: the bounded (size 1) tube buffer overflows as soon as the
        // server firehose starts, which must abort the underlying HTTP request.
        client.stream(request, new DefaultServerSentEventParser()).subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                // deliberately never request - force the buffer to overflow
            }

            @Override
            public void onNext(HttpStreamingEvent item) {}

            @Override
            public void onError(Throwable throwable) {}

            @Override
            public void onComplete() {}
        });

        assertThat(serverAccepted.await(5, SECONDS)).as("the client must connect").isTrue();
        assertThat(clientDisconnected.await(5, SECONDS))
                .as("overflowing the stream buffer must abort the request and close the TCP connection")
                .isTrue();
    }
}
