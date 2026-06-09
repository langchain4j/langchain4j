package dev.langchain4j.http.client.jdk;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.log.LoggingHttpClient;
import dev.langchain4j.http.client.sse.StreamingHttpEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.blockhound.BlockHound;
import reactor.blockhound.BlockingOperationError;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static dev.langchain4j.http.client.HttpMethod.POST;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link JdkHttpClient}'s streaming publisher ({@link HttpClient#executeWithPublisher})
 * does not perform blocking calls on the JDK HTTP client's worker threads (named {@code HttpClient-*}),
 * where the SSE body is read, parsed and dispatched. This is the HTTP-client-layer counterpart of
 * {@code OpenAiStreamingChatModelNonBlockingIT} — same BlockHound technique, exercising only the HTTP
 * client (no chat model). The request is a real OpenAI chat-completions streaming call, built by hand
 * so this stays at the HTTP-client layer (no openai-module dependency).
 * <p>
 * Parameterized over a {@code logging} flag: when on, the pipeline is wrapped in
 * {@link LoggingHttpClient}, whose subscriber logs every event (the per-event log is DEBUG) — a
 * separate code path that must also stay non-blocking. Test logging is asynchronous (see the
 * {@code writingthread} setting in {@code tinylog.properties}, with the logging package at DEBUG so
 * the per-event logging actually fires), so log writes happen off the delivery threads.
 * <p>
 * Scope/caveat: the JDK completes {@code sendAsync(...)} on the shared
 * {@link java.util.concurrent.ForkJoinPool#commonPool()} and delivers the initial
 * {@link dev.langchain4j.http.client.SuccessfulHttpResponse} (and possibly an early buffered chunk)
 * there; BlockHound cannot police the common pool (its idle workers legitimately park and spin).
 * Subsequent chunks stream in on {@code HttpClient-*} workers — the hot path this test covers — while
 * the brief common-pool startup is out of scope.
 * <p>
 * Runs against the real OpenAI endpoint to exercise the full HTTPS / HTTP/2 / real-network-pacing
 * stack. BlockHound is JVM-global, so the recorded violations are shared static state;
 * {@link #resetViolations()} clears them before each test, which keeps the tests order-independent
 * (they must run sequentially). This is an {@code *IT} so its global BlockHound install runs in the
 * failsafe JVM, isolated from the surefire-run {@link StreamingHttpEventPublisherTckTest}.
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class JdkHttpClientNonBlockingIT {

    /**
     * Blocking calls BlockHound observed on a policed thread. Cleared before each test by
     * {@link #resetViolations()}; the streaming test expects none, the self-test expects one.
     */
    private static final List<Throwable> violations = new CopyOnWriteArrayList<>();

    @BeforeAll
    static void installBlockHound() {
        BlockHound.builder()
                // The SSE body is read, parsed and dispatched on the JDK HTTP client's worker threads
                // ("HttpClient-*"). If we block any of these, throughput collapses under concurrency.
                // (Stream startup runs on ForkJoinPool.commonPool, which cannot be policed.)
                .nonBlockingThreadPredicate(prev -> prev.or(t -> t.getName().startsWith("HttpClient-")))
                // Pool bookkeeping, not application blocking: idle workers park on the work queue
                // (getTask), exiting workers acquire the pool's lock to coordinate shutdown (processWorkerExit).
                .allowBlockingCallsInside("java.util.concurrent.ThreadPoolExecutor", "getTask")
                .allowBlockingCallsInside("java.util.concurrent.ThreadPoolExecutor", "processWorkerExit")
                // Async test logging (logging=true): tinylog hands each entry to its writer thread
                // under a monitor (WritingThread.add → Object.notify()). When logging every streamed
                // event, the worker can briefly contend on that monitor and park — the logging
                // backend's internal handoff, not our pipeline. Tolerate it so the per-event logging
                // path stays exercised without flaking. (Everything else, incl. our wrapper/parse, is
                // still policed; this allows blocking only inside WritingThread.add.)
                .allowBlockingCallsInside("org.tinylog.core.WritingThread", "add")
                // Record (don't throw): a thrown error on a worker thread kills the thread but never
                // reaches our subscriber, so the test would pass despite the violation.
                .blockingMethodCallback(method -> violations.add(new BlockingOperationError(method)))
                .install();
    }

    @BeforeAll
    static void warmUp() throws Exception {
        // The first real request triggers one-time, JVM-global lazy I/O on the worker threads (TLS
        // truststore read during the HTTPS handshake, class/JAR loading). Trigger it once here so the
        // measured tests see only steady-state behavior; logging is enabled so the LoggingHttpClient
        // and tinylog classes load here too. Any violations recorded here are wiped by
        // resetViolations() before the first test.
        awaitEvents(newClient(true), request(10));
    }

    @BeforeEach
    void resetViolations() {
        violations.clear();
    }

    @ParameterizedTest(name = "logging={0}")
    @ValueSource(booleans = {false, true})
    void publisher_path_does_not_block_jdk_http_threads(boolean logging) throws Exception {
        // Given: a real OpenAI streaming request, multi-token so the body arrives in several reads.
        Capture capture = awaitEvents(newClient(logging), request(50));

        // Then: stream completed normally and real events arrived...
        assertThat(capture.error()).as("subscriber received an error (logging=%s)", logging).isNull();
        assertThat(capture.received()).as("no events received (logging=%s)", logging).isNotEmpty();
        // ...at least one was delivered on a policed worker thread (so the empty-violations assertion
        // below isn't vacuous). The JDK delivers the SuccessfulHttpResponse — and possibly the first
        // buffered chunk — on the unpoliced common pool, more so with logging which delays the body
        // subscription; later chunks land on HttpClient-* workers. (allMatch would flake on the
        // common-pool events.)
        assertThat(capture.deliveryThreads())
                .as("at least one event must be delivered on a policed JDK HTTP worker thread (logging=%s); "
                        + "delivered on: %s", logging, capture.deliveryThreads())
                .anyMatch(name -> name.startsWith("HttpClient-"));
        // ...and no blocking call was detected on the worker threads anywhere in the pipeline.
        assertThat(violations)
                .as("BlockHound detected blocking calls on JDK HTTP worker threads (logging=%s) — see stack(s) below", logging)
                .isEmpty();
    }

    /**
     * Sanity-checks the harness itself: a blocking call on a policed ("HttpClient-*") thread MUST be
     * recorded. Together with the delivery-thread assertion above this guarantees
     * {@code publisher_path_does_not_block_jdk_http_threads} cannot pass vacuously. Order-independent:
     * {@link #resetViolations()} clears the list first, and the blocking call records its own violation
     * synchronously (the worker thread is joined before the assertion).
     */
    @Test
    void blockHound_detects_blocking_on_a_policed_thread() throws Exception {
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "HttpClient-selftest");
        thread.start();
        thread.join(TimeUnit.SECONDS.toMillis(5));

        assertThat(violations)
                .as("BlockHound must flag a blocking call on a policed streaming thread")
                .isNotEmpty();
    }

    private static HttpClient newClient(boolean logging) {
        HttpClient client = JdkHttpClient.builder().build();
        return logging ? new LoggingHttpClient(client, true, true) : client;
    }

    private static HttpRequest request(int count) {
        // A real OpenAI chat-completions streaming request, built by hand (no openai-module dependency).
        // A multi-token, time-streamed response arrives in several network reads, so the parsing and
        // dispatch run on HttpClient-* workers (and, with logging, exercise per-event logging there).
        return HttpRequest.builder()
                .method(POST)
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer " + System.getenv("OPENAI_API_KEY"))
                .addHeader("Content-Type", "application/json")
                .body("""
                        {
                          "model": "gpt-4o-mini",
                          "stream": true,
                          "max_completion_tokens": 200,
                          "messages": [ { "role": "user", "content": "Count from 1 to %d, one number per line." } ]
                        }""".formatted(count))
                .build();
    }

    private static Capture awaitEvents(HttpClient client, HttpRequest request) throws Exception {
        Flow.Publisher<StreamingHttpEvent> publisher = client.executeWithPublisher(request);
        List<StreamingHttpEvent> received = new CopyOnWriteArrayList<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        // Names of every thread that delivered an event, so the caller can assert the pipeline ran on
        // policed threads (otherwise an empty violations list would prove nothing).
        Set<String> deliveryThreads = ConcurrentHashMap.newKeySet();
        CompletableFuture<Void> done = new CompletableFuture<>();

        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(StreamingHttpEvent event) {
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

    private record Capture(List<StreamingHttpEvent> received, Set<String> deliveryThreads, Throwable error) {}
}
