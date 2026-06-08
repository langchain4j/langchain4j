package dev.langchain4j.model.openai;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.StreamingEvent;
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

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link OpenAiStreamingChatModel#chat(ChatRequest)}'s publisher path does not perform
 * blocking calls on the JDK HTTP client's worker threads (named {@code HttpClient-*}), where the bulk
 * of a streamed response is parsed and dispatched. Uses BlockHound, which reports a
 * {@link reactor.blockhound.BlockingOperationError} if a registered "non-blocking" thread performs a
 * blocking call (Socket read, InputStream read, Thread.sleep, Object.wait, etc.).
 * <p>
 * Scope/caveat: the JDK completes {@code sendAsync(...)} and delivers the first (already-buffered)
 * body chunk on the shared {@link java.util.concurrent.ForkJoinPool#commonPool()}, which BlockHound
 * cannot police (its idle workers legitimately park and spin). So this test covers the steady-state
 * delivery threads ({@code HttpClient-*}) — the throughput-critical hot path — but not the brief
 * stream-startup work that runs on the common pool. TODO
 * <p>
 * Runs against the real OpenAI endpoint to exercise the full HTTPS / HTTP/2 / real-network-pacing
 * stack — these code paths often hide blocking calls that WireMock-based plain HTTP wouldn't reach.
 * <p>
 * Parameterized over {@code logRequests}/{@code logResponses}: with logging enabled, the pipeline
 * goes through {@link dev.langchain4j.http.client.log.LoggingHttpClient}'s wrapping subscriber, which
 * is a separate code path that must also stay non-blocking. (Test logging is asynchronous — see the
 * {@code writingthread} setting in {@code tinylog.properties} — so log writes happen off the delivery
 * threads rather than blocking them.)
 * <p>
 * BlockHound is JVM-global, so the recorded violations are shared static state. {@link #resetViolations()}
 * clears them before each test, which keeps the tests order-independent (they must run sequentially).
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiStreamingChatModelNonBlockingIT {

    // TODO similar test for http client, AI Service and other providers?

    /**
     * Recorded BlockHound violations. Cleared per test in {@link #resetViolations()}; asserted empty
     * after each test. A list (not a flag) so debugging can show all offending calls.
     */
    private static final List<Throwable> violations = new CopyOnWriteArrayList<>();

    @BeforeAll
    static void installBlockHound() {
        BlockHound.builder()
                // The bulk of a streamed response is parsed and dispatched on the JDK HTTP client's
                // worker threads ("HttpClient-*"). If we block any of these, throughput collapses
                // under concurrency. BlockHound enforces this. (Stream startup runs on
                // ForkJoinPool.commonPool, which cannot be policed and is out of scope here.)
                .nonBlockingThreadPredicate(prev -> prev.or(t -> t.getName().startsWith("HttpClient-")))
                // Pool bookkeeping, not application blocking: idle workers park on the work queue
                // (getTask), exiting workers acquire the pool's lock to coordinate shutdown
                // (processWorkerExit).
                .allowBlockingCallsInside("java.util.concurrent.ThreadPoolExecutor", "getTask")
                .allowBlockingCallsInside("java.util.concurrent.ThreadPoolExecutor", "processWorkerExit")
                // Record (don't throw): a thrown error on a worker thread kills the thread but never
                // reaches our subscriber, so the test would pass despite the violation. Recording lets
                // us assert on it after the stream completes.
                .blockingMethodCallback(method -> violations.add(new BlockingOperationError(method)))
                .install();
    }

    @BeforeAll
    static void warmUp() throws Exception {
        // The first real request triggers one-time, JVM-global lazy I/O on the worker threads (TLS
        // truststore read during the HTTPS handshake, class/JAR loading). Trigger it once here so the
        // measured tests see only steady-state behavior. Logging is enabled so the logging path's
        // classes load here too. Order relative to installBlockHound() is irrelevant: the
        // initialization happens regardless, and any violations recorded here are wiped by
        // resetViolations() before the first test.
        awaitStream(newModel(true), request(10));
    }

    @BeforeEach
    void resetViolations() {
        violations.clear();
    }

    @ParameterizedTest(name = "logging={0}")
    @ValueSource(booleans = {false, true})
    void publisher_path_does_not_block_jdk_http_threads(boolean logging) throws Exception {
        // Given: real OpenAI streaming endpoint, a multi-token response so the body arrives in several
        // network reads (see request()).
        StreamCapture capture = awaitStream(newModel(logging), request(50));

        // Then: stream completed normally, real events arrived, and no blocking call was detected on
        // the JDK HTTP worker threads anywhere in the pipeline.
        assertThat(capture.error()).as("subscriber received an error (logging=%s)", logging).isNull();
        assertThat(capture.received()).as("no events received (logging=%s)", logging).isNotEmpty();
        // Every event must be delivered on a policed worker thread, so the no-violations assertion
        // covers the whole pipeline (isNotEmpty guards the vacuous all-match-on-empty case). The real
        // streaming API flushes headers before the first token, so all body chunks land on
        // HttpClient-* workers, never on the common-pool sendAsync completion.
        assertThat(capture.deliveryThreads())
                .as("every event must be delivered on a policed JDK HTTP worker thread (logging=%s); delivered on: %s",
                        logging, capture.deliveryThreads())
                .isNotEmpty()
                .allMatch(name -> name.startsWith("HttpClient-"));
        assertThat(violations)
                .as("BlockHound detected blocking calls on JDK HTTP worker threads (logging=%s) — see stack(s) below", logging)
                .isEmpty();
    }

    /**
     * Sanity-checks the harness itself: a blocking call on a policed ("HttpClient-*") thread MUST be
     * recorded. Together with the delivery-thread assertion above — which proves the real pipeline
     * runs on such threads — this guarantees {@code publisher_path_does_not_block_*} cannot pass
     * vacuously. If BlockHound ever stopped policing the worker threads (wrong predicate, pipeline
     * moved to an unpoliced pool, install failing, etc.), this would fail. Order-independent:
     * {@link #resetViolations()} clears the list first, and the blocking call records its own
     * violation synchronously (the worker thread is joined before the assertion).
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

    private static OpenAiStreamingChatModel newModel(boolean logging) {
        return OpenAiStreamingChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_4_O_MINI)
                .maxCompletionTokens(200)
                .logRequests(logging)
                .logResponses(logging)
                .build();
    }

    private static ChatRequest request(int n) {
        // A multi-token response, streamed over time, ensures the body arrives in several network
        // reads — so events are delivered on the JDK HttpClient-* worker threads, not only on the
        // first (buffered) chunk that the JDK hands off on ForkJoinPool.commonPool. This keeps the
        // non-vacuity guard (and thus the blocking check on the worker threads) reliable.
        return ChatRequest.builder()
                .messages(UserMessage.from("Count from 1 to %s, one number per line.".formatted(n)))
                .build();
    }

    private static StreamCapture awaitStream(OpenAiStreamingChatModel model, ChatRequest request) throws Exception {
        Flow.Publisher<StreamingEvent> publisher = model.chat(request);
        List<StreamingEvent> received = new CopyOnWriteArrayList<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        // Names of every thread that delivered an event, so the caller can assert the pipeline ran on
        // a policed thread (otherwise an empty violations list would prove nothing).
        Set<String> deliveryThreads = ConcurrentHashMap.newKeySet();
        CompletableFuture<Void> done = new CompletableFuture<>();

        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(StreamingEvent event) {
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
        return new StreamCapture(received, deliveryThreads, error.get());
    }

    private record StreamCapture(List<StreamingEvent> received, Set<String> deliveryThreads, Throwable error) {}
}
