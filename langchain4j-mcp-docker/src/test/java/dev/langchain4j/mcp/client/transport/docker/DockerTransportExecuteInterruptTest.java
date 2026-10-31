package dev.langchain4j.mcp.client.transport.docker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.mcp.client.transport.McpOperationHandler;
import dev.langchain4j.mcp.protocol.McpInitializationNotification;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * {@link DockerMcpTransport#execute} has two blocking sites that sit inside its broad
 * {@code catch (Exception e)} block, which hands every exception to
 * {@code future.completeExceptionally} without ever restoring the thread's interrupt flag:
 *
 * <pre>
 * callback.awaitStarted(attachTimeout.toMillis(), TimeUnit.MILLISECONDS);
 * ...
 * Thread.sleep(100); // only on the fire-and-forget path
 * </pre>
 *
 * <p>An interrupt delivered while the transport is parked at either site is therefore swallowed,
 * and the thread's interrupt flag is left cleared — so the cancellation is silently lost and the
 * surrounding framework has no way to observe it. The sibling method
 * {@code awaitResponseAndDetach} in the same class already shows the intended handling
 * (re-interrupt, then {@code completeExceptionally}); {@code execute} should behave the same.
 *
 * <p>Both phases are exercised against a stub Docker daemon so that the real docker-java client
 * issues the real blocking calls. The interrupt flag is observed on the calling thread right after
 * {@code sendMessage} returns, which is exactly when {@code execute} has finished its
 * catch/finally processing on that same thread.
 *
 * <p>Note on the class name: these tests execute the real {@link DockerResultCallback} code path,
 * which binds its static {@code LOG} field on first use. {@code DockerResultCallbackTest} relies on
 * mocking {@code LoggerFactory} while the class is still uninitialized, so it must run first — and
 * with the default class-name ordering, "DockerTransport..." sorts after "DockerResultCallback..."
 * while "DockerMcpTransport..." would not.
 */
class DockerTransportExecuteInterruptTest {

    /** A stub response that satisfies docker-java's parsing for each endpoint {@code start()} hits. */
    private static String bodyFor(String path) {
        if (path.equals("/images/create")) {
            return "{\"status\":\"Download complete\"}";
        }
        if (path.startsWith("/containers/create")) {
            return "{\"Id\":\"stub-container-id\",\"Warnings\":[]}";
        }
        if (path.endsWith("/wait")) {
            return "{\"StatusCode\":0}";
        }
        return "";
    }

    /**
     * The interrupt arrives while {@code execute} is parked in
     * {@code callback.awaitStarted(...)}: the stub daemon withholds the attach response headers, so
     * the attachment start never completes until the test interrupts.
     */
    @Test
    void shouldRestoreInterruptStatusWhenAttachmentStartIsInterrupted() throws Exception {
        CountDownLatch attachRequested = new CountDownLatch(1);
        CountDownLatch releaseAttach = new CountDownLatch(1);
        HttpServer daemon = stubDaemon(attachRequested, releaseAttach, false);
        daemon.start();

        try {
            CompletableFuture<Boolean> interruptStatus = executeThenInterrupt(daemon, attachRequested);
            assertThat(interruptStatus.get(15, TimeUnit.SECONDS))
                    .as("interrupt status must survive the attachment-start phase of execute()")
                    .isTrue();
        } finally {
            releaseAttach.countDown();
            daemon.stop(0);
        }
    }

    /**
     * The fire-and-forget path: the attachment starts successfully, the request is written to the
     * container's stdin, and {@code execute} settles in {@code Thread.sleep(100)} before tearing the
     * attachment down. The interrupt is delivered right after the attach response has been written
     * back, so it lands inside (or immediately before) that sleep window — either way the pending
     * flag must survive {@code execute}'s catch block.
     */
    @Test
    void shouldRestoreInterruptStatusWhenPostWriteSleepIsInterrupted() throws Exception {
        CountDownLatch attachResponded = new CountDownLatch(1);
        CountDownLatch neverAwaited = new CountDownLatch(1);
        HttpServer daemon = stubDaemon(attachResponded, neverAwaited, true);
        daemon.start();

        try {
            CompletableFuture<Boolean> interruptStatus = executeThenInterrupt(daemon, attachResponded);
            assertThat(interruptStatus.get(15, TimeUnit.SECONDS))
                    .as("interrupt status must survive the post-write settle sleep of execute()")
                    .isTrue();
        } finally {
            neverAwaited.countDown();
            daemon.stop(0);
        }
    }

    /**
     * A stub Docker daemon that answers every request {@code start()} needs, and treats the attach
     * request specially: when {@code respondToAttach} is false the response headers are withheld
     * (parking {@code execute} inside {@code awaitStarted}); when true the response is written back
     * first and only then is {@code attachSignal} released, so the test interrupts after the
     * attachment is live.
     */
    private static HttpServer stubDaemon(CountDownLatch attachSignal, CountDownLatch release, boolean respondToAttach)
            throws Exception {
        HttpServer daemon = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        daemon.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(4));
        daemon.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.contains("/attach")) {
                if (!respondToAttach) {
                    attachSignal.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
                attachSignal.countDown();
                return;
            }
            writeJson(exchange, bodyFor(path));
        });
        return daemon;
    }

    /**
     * Starts the transport against the stub daemon, runs {@code sendMessage} (the fire-and-forget
     * entry point into {@code execute}) on a dedicated thread, waits until the stub daemon has seen
     * the attach request, interrupts the thread, and reports whether the interrupt flag survived on
     * the thread that called {@code sendMessage}.
     */
    private static CompletableFuture<Boolean> executeThenInterrupt(HttpServer daemon, CountDownLatch reached)
            throws Exception {
        DockerMcpTransport transport = DockerMcpTransport.builder()
                .dockerHost("tcp://127.0.0.1:" + daemon.getAddress().getPort())
                .image("alpine")
                .build();
        transport.start(mock(McpOperationHandler.class));

        CompletableFuture<Boolean> interruptStatus = new CompletableFuture<>();
        Thread thread = new Thread(() -> {
            try {
                transport.sendMessage(new McpInitializationNotification());
                interruptStatus.complete(Thread.currentThread().isInterrupted());
            } catch (RuntimeException unexpected) {
                interruptStatus.completeExceptionally(unexpected);
            }
        });
        thread.setDaemon(true);
        thread.start();

        try {
            assertThat(reached.await(15, TimeUnit.SECONDS))
                    .as("the stub daemon never saw the attach request")
                    .isTrue();
            thread.interrupt();
        } finally {
            thread.interrupt();
        }
        return interruptStatus;
    }

    private static void writeJson(com.sun.net.httpserver.HttpExchange exchange, String body)
            throws java.io.IOException {
        String payload = body + "\n";
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
        exchange.close();
    }
}
