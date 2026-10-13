package dev.langchain4j.http.client;

import static dev.langchain4j.http.client.HttpMethod.GET;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies the cancellation contract of {@link HttpClient#executeAsync(HttpRequest)}, common across HTTP
 * client implementations. Subclass and provide a {@link #client()}.
 * <ul>
 *     <li>Every implementation must <b>release the caller</b>: cancelling the returned future completes it
 *         with {@link CancellationException}, without waiting for the response.</li>
 *     <li>Implementations that can <b>abort the in-flight request</b> additionally close the TCP connection
 *         on cancellation. This is verified with a bare {@link ServerSocket} that accepts the connection and
 *         never responds: the server observes the client closing the socket (its {@code read()} returns
 *         {@code -1}). Implementations that do not support this (e.g. clients that buffer the whole response)
 *         override {@link #cancelAbortsConnection()} to return {@code false}.</li>
 * </ul>
 */
public abstract class HttpClientCancellationIT {

    protected abstract HttpClient client();

    /**
     * Whether cancelling the future aborts the in-flight request and closes the connection.
     * Defaults to {@code true}; override for clients that cannot abort an in-flight request.
     */
    protected boolean cancelAbortsConnection() {
        return true;
    }

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
    void cancelling_the_future_releases_the_caller() throws Exception {

        CountDownLatch serverAccepted = startNeverRespondingServer(null);

        CompletableFuture<SuccessfulHttpResponse> future = client().executeAsync(request());

        assertThat(serverAccepted.await(5, SECONDS))
                .as("the client must connect to the server")
                .isTrue();

        boolean cancelled = future.cancel(true);

        assertThat(cancelled).isTrue();
        assertThat(future).isCancelled();
        // the caller is released immediately, without waiting for the (never-arriving) response
        assertThatThrownBy(() -> future.get(1, SECONDS)).isInstanceOf(CancellationException.class);
    }

    @Test
    void cancelling_the_future_aborts_the_request_and_closes_the_connection() throws Exception {

        assumeTrue(cancelAbortsConnection(), "this client does not abort an in-flight request on cancellation");

        CountDownLatch clientDisconnected = new CountDownLatch(1);
        CountDownLatch serverAccepted = startNeverRespondingServer(clientDisconnected);

        CompletableFuture<SuccessfulHttpResponse> future = client().executeAsync(request());

        assertThat(serverAccepted.await(5, SECONDS))
                .as("the client must connect to the server")
                .isTrue();

        future.cancel(true);

        assertThat(future).isCancelled();
        assertThat(clientDisconnected.await(5, SECONDS))
                .as("cancelling the future must abort the request and close the TCP connection (server sees EOF)")
                .isTrue();
    }

    /**
     * Starts a server that accepts a single connection and never responds. Counts down the returned latch
     * once the connection is accepted, and (if provided) {@code clientDisconnected} once the client closes
     * the socket (the server's {@code read()} returns {@code -1}).
     */
    private CountDownLatch startNeverRespondingServer(CountDownLatch clientDisconnected) {
        CountDownLatch serverAccepted = new CountDownLatch(1);
        serverExecutor.submit(() -> {
            try {
                Socket socket = serverSocket.accept();
                acceptedSocket.set(socket);
                serverAccepted.countDown();
                InputStream in = socket.getInputStream();
                // Never respond. Drain the request bytes, then block reading until the client closes the
                // socket: read() returns -1 only when the connection is actually closed by the client.
                while (in.read() != -1) {
                    // discard request bytes; blocks waiting for more / for close once the request is consumed
                }
                if (clientDisconnected != null) {
                    clientDisconnected.countDown();
                }
            } catch (IOException ignored) {
                // accept()/read() interrupted on teardown
            }
        });
        return serverAccepted;
    }

    private HttpRequest request() {
        return HttpRequest.builder()
                .method(GET)
                .url("http://localhost:" + serverSocket.getLocalPort() + "/never-responds")
                .build();
    }
}
