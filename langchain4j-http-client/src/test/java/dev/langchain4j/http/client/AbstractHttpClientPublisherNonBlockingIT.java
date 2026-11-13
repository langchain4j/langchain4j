package dev.langchain4j.http.client;

import static dev.langchain4j.http.client.HttpMethod.GET;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.http.client.sse.HttpStreamingEvent;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.blockhound.BlockHound;
import reactor.blockhound.BlockingOperationError;

/**
 * Shared contract test (TCK) for the reactive {@link HttpClient#stream(HttpRequest, dev.langchain4j.http.client.sse.ServerSentEventParser)
 * stream()} path of an {@link HttpClient}: it verifies that parsing and dispatching a server-sent-event response does
 * not perform <b>blocking</b> calls on the transport threads that carry that dispatch. Every client that claims a
 * genuinely non-blocking reactive stream should extend this class so the guarantee is enforced, not just asserted in
 * prose.
 * <p>
 * It uses <a href="https://github.com/reactor/BlockHound">BlockHound</a>, which reports a
 * {@link BlockingOperationError} if a thread registered as "non-blocking" performs a blocking call (socket read,
 * {@code InputStream} read, {@code Thread.sleep}, {@code Object.wait}, …). The response is served by a local,
 * plain-HTTP SSE server (below) — no TLS, no external endpoint, no API key — so only the client's own parse/dispatch
 * pipeline is policed, not a third party's connection setup.
 * <p>
 * A subclass provides the client via {@link #newClient(boolean)} and the {@linkplain #policedThreadNamePrefix() name
 * prefix} of the transport threads that deliver body chunks (JDK: {@code HttpClient-}; Apache: {@code httpclient-dispatch}).
 * It is parameterized over logging so the logging wrapper's separate code path is policed too.
 * <p>
 * Extended by the JDK and Apache clients (both fully reactive). The OkHttp client is <b>deliberately not</b> extended:
 * OkHttp exposes the response body only as a blocking source, so it reads and parses it with a blocking call on a
 * per-connection thread (verified — it trips this test with {@code SocketInputStream#read}). That thread-pinning is an
 * inherent OkHttp limitation, not a bug, so a non-blocking assertion does not apply to it.
 * <p>
 * BlockHound is JVM-global, so recorded violations are shared state; {@link #resetViolations()} clears them before
 * each test to keep them order-independent (they run sequentially).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractHttpClientPublisherNonBlockingIT {

    /** Blocking calls BlockHound observed on a policed thread. Cleared before each test by {@link #resetViolations()}. */
    private final List<Throwable> violations = new CopyOnWriteArrayList<>();

    private SseServer server;

    /**
     * The client under test. {@code logging} wraps it so the logging code path (which logs each streamed event on the
     * transport thread) is policed too.
     */
    protected abstract HttpClient newClient(boolean logging);

    /**
     * Name prefix of the transport threads that deliver body chunks and must never block. For the JDK client these are
     * its workers ({@code HttpClient-}); override for a transport that dispatches on differently-named threads.
     */
    protected abstract String policedThreadNamePrefix();

    @BeforeAll
    void installBlockHound() {
        BlockHound.builder()
                // The SSE body is read, parsed and dispatched on the transport's worker threads. If we block any of
                // these, throughput collapses under concurrency; BlockHound enforces it. (Stream startup may run on
                // ForkJoinPool.commonPool, which cannot be policed and is out of scope here.)
                .nonBlockingThreadPredicate(prev -> prev.or(t -> t.getName().startsWith(policedThreadNamePrefix())))
                // Pool bookkeeping, not application blocking: idle workers park on the work queue (getTask), exiting
                // workers acquire the pool's lock to coordinate shutdown (processWorkerExit).
                .allowBlockingCallsInside("java.util.concurrent.ThreadPoolExecutor", "getTask")
                .allowBlockingCallsInside("java.util.concurrent.ThreadPoolExecutor", "processWorkerExit")
                // Async test logging (logging=true): tinylog hands each entry to its writer thread under a monitor
                // (WritingThread.add → Object.notify()). The worker can briefly contend on that monitor and park — the
                // logging backend's internal handoff, not our pipeline. Tolerate it so logging=true doesn't flake.
                .allowBlockingCallsInside("org.tinylog.core.WritingThread", "add")
                // Record (don't throw): a thrown error on a worker thread kills the thread but never reaches our
                // subscriber, so the test would pass despite the violation. Recording lets us assert on it afterwards.
                .blockingMethodCallback(method -> violations.add(new BlockingOperationError(method)))
                .install();
    }

    @BeforeAll
    void startServerAndWarmUp() throws Exception {
        server = SseServer.start();
        // The first request lazily loads the client/parse classes on the worker threads (FileInputStream reading .class
        // from jars). Trigger it once so the measured tests see only steady-state behavior; logging is enabled so the
        // logging path's classes load here too. Any violations recorded here are wiped by resetViolations().
        awaitEvents(newClient(true), 10);
    }

    @AfterAll
    void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    @BeforeEach
    void resetViolations() {
        violations.clear();
    }

    @ParameterizedTest(name = "logging={0}")
    @ValueSource(booleans = {false, true})
    void publisher_path_does_not_block_the_transport_threads(boolean logging) throws Exception {
        // Given: a multi-event response that arrives in several reads, exercising the pipeline across many chunks.
        Capture capture = awaitEvents(newClient(logging), 50);

        // Then: the stream completed normally and real events arrived...
        assertThat(capture.error).as("subscriber received an error (logging=%s)", logging).isNull();
        assertThat(capture.received).as("no events received (logging=%s)", logging).isNotEmpty();

        // ...at least one on a policed transport thread (so the empty-violations assertion below isn't vacuous). The
        // first event may be delivered on an unpoliced startup pool; later chunks land on policed workers.
        assertThat(capture.deliveryThreads)
                .as("at least one event must be delivered on a policed transport thread (logging=%s); delivered on: %s",
                        logging, capture.deliveryThreads)
                .anyMatch(name -> name.startsWith(policedThreadNamePrefix()));

        // ...and no blocking call was detected on the transport threads anywhere in the pipeline.
        assertThat(violations)
                .as("BlockHound detected blocking calls on the transport threads (logging=%s) — see stack(s) below",
                        logging)
                .isEmpty();
    }

    /**
     * Sanity-checks the harness itself: a blocking call on a policed thread MUST be recorded. Together with the
     * delivery-thread assertion above, this guarantees the non-blocking test cannot pass vacuously (if BlockHound ever
     * stopped policing the transport threads, this would fail).
     */
    @Test
    void blockHound_detects_blocking_on_a_policed_thread() throws Exception {
        Thread thread = new Thread(
                () -> {
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                },
                policedThreadNamePrefix() + "selftest");
        thread.start();
        thread.join(TimeUnit.SECONDS.toMillis(5));

        assertThat(violations)
                .as("BlockHound must flag a blocking call on a policed transport thread")
                .isNotEmpty();
    }

    private Capture awaitEvents(HttpClient client, int count) throws Exception {
        HttpRequest request = HttpRequest.builder()
                .method(GET)
                .url(server.url(count))
                .build();

        Flow.Publisher<HttpStreamingEvent> publisher = client.stream(request);
        List<HttpStreamingEvent> received = new CopyOnWriteArrayList<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        Set<String> deliveryThreads = ConcurrentHashMap.newKeySet();
        CompletableFuture<Void> done = new CompletableFuture<>();

        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(HttpStreamingEvent event) {
                deliveryThreads.add(Thread.currentThread().getName());
                received.add(event);
            }

            @Override
            public void onError(Throwable throwable) {
                error.set(throwable);
                done.complete(null);
            }

            @Override
            public void onComplete() {
                done.complete(null);
            }
        });

        done.get(30, TimeUnit.SECONDS);
        return new Capture(received, deliveryThreads, error.get());
    }

    private record Capture(List<HttpStreamingEvent> received, Set<String> deliveryThreads, Throwable error) {}

    /**
     * A minimal local HTTP/1.1 server that streams {@code count} server-sent events ({@code data: i\n\n}) with a small
     * gap between them (so they arrive in several network reads on the client), then closes the connection. Plain HTTP,
     * so no TLS handshake / truststore I/O reaches the client's worker threads. Its own accept/stream work runs on
     * {@code sse-server} threads, which are never policed.
     */
    static final class SseServer {

        private final ServerSocket serverSocket;
        private final ExecutorService workers;
        private volatile boolean running = true;

        static SseServer start() throws IOException {
            return new SseServer();
        }

        private SseServer() throws IOException {
            this.serverSocket = new ServerSocket(0);
            this.workers = Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(runnable, "sse-server");
                thread.setDaemon(true);
                return thread;
            });
            this.workers.submit(this::acceptLoop);
        }

        String url(int count) {
            return "http://localhost:" + serverSocket.getLocalPort() + "/?count=" + count;
        }

        private void acceptLoop() {
            while (running) {
                try {
                    Socket socket = serverSocket.accept();
                    workers.submit(() -> handle(socket));
                } catch (IOException e) {
                    return; // socket closed on stop()
                }
            }
        }

        private void handle(Socket socket) {
            try (socket) {
                socket.setTcpNoDelay(true);
                int count = readRequestAndParseCount(socket.getInputStream());
                OutputStream out = socket.getOutputStream();
                out.write(("HTTP/1.1 200 OK\r\n"
                                + "Content-Type: text/event-stream\r\n"
                                + "Connection: close\r\n\r\n")
                        .getBytes(UTF_8));
                out.flush();
                for (int i = 1; i <= count; i++) {
                    out.write(("data: " + i + "\n\n").getBytes(UTF_8));
                    out.flush();
                    Thread.sleep(2);
                }
            } catch (Exception ignored) {
                // client closed / test teardown
            }
        }

        private static int readRequestAndParseCount(InputStream in) throws IOException {
            ByteArrayOutputStream head = new ByteArrayOutputStream();
            int b;
            while ((b = in.read()) != -1) {
                head.write(b);
                byte[] a = head.toByteArray();
                int n = a.length;
                if (n >= 4 && a[n - 4] == '\r' && a[n - 3] == '\n' && a[n - 2] == '\r' && a[n - 1] == '\n') {
                    break;
                }
            }
            String text = head.toString(UTF_8);
            int lineEnd = text.indexOf("\r\n");
            String requestLine = lineEnd < 0 ? text : text.substring(0, lineEnd);
            int idx = requestLine.indexOf("count=");
            if (idx < 0) {
                return 30;
            }
            String tail = requestLine.substring(idx + "count=".length());
            int end = 0;
            while (end < tail.length() && Character.isDigit(tail.charAt(end))) {
                end++;
            }
            return end == 0 ? 30 : Integer.parseInt(tail.substring(0, end));
        }

        void stop() {
            running = false;
            try {
                serverSocket.close();
            } catch (IOException ignored) {
                // already closed
            }
            workers.shutdownNow();
        }
    }
}
