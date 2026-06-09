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
import java.util.concurrent.*;
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
 * Scope/caveat: the JDK completes {@code sendAsync(...)} — handling the response headers and
 * subscribing to the body — on the shared {@link java.util.concurrent.ForkJoinPool#commonPool()},
 * and may deliver an early, already-buffered body chunk there too (more likely with logging, which
 * delays the body subscription). BlockHound cannot police the common pool (its idle workers
 * legitimately park and spin). Subsequent chunks stream in on {@code HttpClient-*} workers — the
 * throughput-critical hot path this test covers — while the brief common-pool startup/first chunk is
 * out of scope.
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

    // TODO similar test for AI Service and other providers?

    /**
     * Blocking calls BlockHound observed on a policed thread.
     * Cleared before each test by {@link #resetViolations()}.
     */
    private static final List<Throwable> violations = new CopyOnWriteArrayList<>();

    @BeforeEach
    void resetViolations() {
        violations.clear();
    }

    @BeforeAll
    static void installBlockHound() {
        BlockHound.builder()
                // The bulk of a streamed response is parsed and dispatched on the JDK HTTP client's
                // worker threads ("HttpClient-*"). If we block any of these, throughput collapses
                // under concurrency. BlockHound enforces this. (Stream startup runs on
                // ForkJoinPool.commonPool, which cannot be policed and is out of scope here.)
                .nonBlockingThreadPredicate(prev -> prev.or(t -> t.getName().startsWith("HttpClient-")))
                // Pool bookkeeping, not application blocking: idle workers park on the work queue
                // (getTask), exiting workers acquire the pool's lock to coordinate shutdown (processWorkerExit).
                .allowBlockingCallsInside("java.util.concurrent.ThreadPoolExecutor", "getTask")
                .allowBlockingCallsInside("java.util.concurrent.ThreadPoolExecutor", "processWorkerExit")
                // Async test logging (logging=true): LoggingHttpClient logs every streamed event at
                // DEBUG on the worker thread, and tinylog hands each entry to its writer thread under a
                // monitor (WritingThread.add → Object.notify()). The worker can briefly contend on that
                // monitor and park — the logging backend's internal handoff, not our pipeline. Tolerate
                // it so logging=true doesn't flake. (Only blocking inside WritingThread.add is allowed;
                // our pipeline is still fully policed.)
                .allowBlockingCallsInside("org.tinylog.core.WritingThread", "add")
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

    @ParameterizedTest(name = "logging={0}")
    @ValueSource(booleans = {false, true})
    void publisher_path_does_not_block_jdk_http_threads(boolean logging) throws Exception {
        // Given: real OpenAI streaming endpoint and a multi-token response that exercises the
        // streaming pipeline across many chunks (see request()).
        StreamCapture capture = awaitStream(newModel(logging), request(50));

        // Then: stream completed normally, real events arrived, and no blocking call was detected on
        // the JDK HTTP worker threads anywhere in the pipeline.
        assertThat(capture.error()).isNull();
        assertThat(capture.received()).isNotEmpty();

        // Non-vacuity guard: at least one event must be delivered on a policed worker thread, so the
        // empty-violations assertion below isn't vacuous. The JDK may deliver the first, already-
        // buffered chunk on the unpoliced common pool — more so with logging, which delays the body
        // subscription past the first frame's arrival — but a multi-token response guarantees later
        // chunks land on HttpClient-* workers. (allMatch would be wrong: it flakes on that first chunk.)
        assertThat(capture.deliveryThreads()).anyMatch(name -> name.startsWith("HttpClient-"));

        assertThat(violations).isEmpty();
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
        // A multi-token response, streamed over time, arrives in several network reads so that later
        // chunks are delivered on HttpClient-* workers (the JDK may deliver the first, already-buffered
        // chunk on the common pool). This both exercises the parsing/dispatch pipeline across many
        // chunks and keeps the non-vacuity guard — and thus the blocking check — reliable.
        return ChatRequest.builder()
                .messages(UserMessage.from("Count from 1 to %s, one number per line.".formatted(n)))
                .build();
    }

    private static StreamCapture awaitStream(OpenAiStreamingChatModel model, ChatRequest request) throws Exception {
        Flow.Publisher<StreamingEvent> publisher = model.chat(request);
        List<StreamingEvent> received = new CopyOnWriteArrayList<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
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
