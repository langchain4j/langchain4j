package dev.langchain4j.mcp.client.transport.docker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.mcp.client.transport.McpOperationHandler;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * The image pull is not the only blocking Docker call in {@link DockerMcpTransport#start}.
 * Once the pull completes, {@code start} also blocks on the container it just created:
 *
 * <pre>
 * dockerClient.startContainerCmd(containerId).exec();
 * dockerClient.waitContainerCmd(containerId).start().awaitStarted();
 * </pre>
 *
 * <p>{@code awaitStarted()} is declared to throw {@link InterruptedException}, but it sits inside a
 * broad {@code catch (Exception e)} block. An interrupt delivered while the container is starting is
 * therefore swallowed, and the caller receives a {@link RuntimeException} with the thread's interrupt
 * flag already cleared — so the cancellation is silently lost and the surrounding framework has no way
 * to observe it.
 *
 * <p>Both phases are exercised against a stub Docker daemon so that the real docker-java client issues
 * the real blocking calls. The image-pull phase already restores the flag (fixed in #6411, issue
 * #6410); it is included as a companion so the two blocking phases of {@code start} are covered side by
 * side and a regression in either one is caught. The container-start phase is the one that still drops
 * the flag.
 */
class DockerMcpTransportStartInterruptTest {

    /** A stub response that satisfies docker-java's parsing for each endpoint {@code start()} hits. */
    private static String bodyFor(String path) {
        if (path.equals("/images/create")) {
            // PullImageResultCallback.isPullSuccessIndicated() requires one of a few known statuses.
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
     * Image-pull phase of {@code start}: the interrupt arrives while the pull is in flight. The flag is
     * already restored here (#6411); this test keeps that behaviour pinned as the sibling case.
     */
    @Test
    void shouldRestoreInterruptStatusWhenImagePullIsInterrupted() throws Exception {
        CountDownLatch pullRequested = new CountDownLatch(1);
        CountDownLatch releasePull = new CountDownLatch(1);
        HttpServer daemon = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        daemon.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(4));
        daemon.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/images/create")) {
                // Hold the response back so the pull is still in flight when the test interrupts.
                pullRequested.countDown();
                try {
                    releasePull.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
                return;
            }
            writeJson(exchange, bodyFor(path));
        });
        daemon.start();

        try {
            CompletableFuture<Boolean> interruptStatus = startThenInterrupt(daemon, pullRequested);
            assertThat(interruptStatus.get(15, TimeUnit.SECONDS))
                    .as("interrupt status must survive the image-pull phase of start()")
                    .isTrue();
        } finally {
            releasePull.countDown();
            daemon.stop(0);
        }
    }

    /**
     * Container-start phase of {@code start}: the pull and the container creation both succeed, then the
     * interrupt arrives while the transport is parked in
     * {@code waitContainerCmd(containerId).start().awaitStarted()}. This is the site that still loses
     * the flag, because the whole {@code try} block is wrapped in {@code catch (Exception e)}.
     */
    @Test
    void shouldRestoreInterruptStatusWhenContainerStartIsInterrupted() throws Exception {
        CountDownLatch waitRequested = new CountDownLatch(1);
        CountDownLatch releaseWait = new CountDownLatch(1);
        HttpServer daemon = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        daemon.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(4));
        daemon.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.endsWith("/wait")) {
                // awaitStarted() unblocks when response headers arrive, so withholding them parks
                // start() inside awaitStarted() with the interrupt flag still meaningful.
                waitRequested.countDown();
                try {
                    releaseWait.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
                return;
            }
            writeJson(exchange, bodyFor(path));
        });
        daemon.start();

        try {
            CompletableFuture<Boolean> interruptStatus = startThenInterrupt(daemon, waitRequested);
            assertThat(interruptStatus.get(15, TimeUnit.SECONDS))
                    .as("interrupt status must survive the container-start phase of start()")
                    .isTrue();
        } finally {
            releaseWait.countDown();
            daemon.stop(0);
        }
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

    /**
     * Runs {@code start()} on a dedicated thread, waits until the stub daemon has seen the request the
     * transport should be blocked on, interrupts the thread, and reports whether the interrupt flag
     * survived on the thread that caught the resulting {@link RuntimeException}.
     */
    private static CompletableFuture<Boolean> startThenInterrupt(HttpServer daemon, CountDownLatch reached)
            throws Exception {
        DockerMcpTransport transport = DockerMcpTransport.builder()
                .dockerHost("tcp://127.0.0.1:" + daemon.getAddress().getPort())
                .image("alpine")
                .build();

        CompletableFuture<Boolean> interruptStatus = new CompletableFuture<>();
        Thread thread = new Thread(() -> {
            try {
                transport.start(mock(McpOperationHandler.class));
                interruptStatus.completeExceptionally(new AssertionError("start unexpectedly completed"));
            } catch (RuntimeException expected) {
                interruptStatus.complete(Thread.currentThread().isInterrupted());
            }
        });
        thread.setDaemon(true);
        thread.start();

        try {
            assertThat(reached.await(15, TimeUnit.SECONDS))
                    .as("the stub daemon never saw the request start() should be blocked on")
                    .isTrue();
            thread.interrupt();
        } finally {
            thread.interrupt();
        }
        return interruptStatus;
    }
}
