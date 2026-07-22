package dev.langchain4j.reactive.streaming;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.StreamingEvent;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.blockhound.BlockHound;
import reactor.blockhound.BlockingOperationError;

/**
 * Shared contract test (TCK) for the reactive publisher path of a {@link StreamingChatModel}: it verifies that
 * {@link StreamingChatModel#chat(ChatRequest)}'s {@code Publisher<StreamingEvent>} does not perform <b>blocking</b>
 * calls on the transport's non-blocking worker threads (where a streamed response is parsed and dispatched). Every
 * provider that claims a genuinely non-blocking reactive stream should extend this class so the guarantee is
 * enforced, not just asserted in prose.
 * <p>
 * It uses <a href="https://github.com/reactor/BlockHound">BlockHound</a>, which reports a
 * {@link BlockingOperationError} if a thread registered as "non-blocking" performs a blocking call (socket read,
 * {@code InputStream} read, {@code Thread.sleep}, {@code Object.wait}, …). By default the policed threads are the JDK
 * HTTP client's workers ({@code HttpClient-*}); a transport with different worker-thread names (e.g. an event-loop
 * pool) overrides {@link #policedThreadNamePrefix()}.
 * <p>
 * A subclass provides the model via {@link #newModel(boolean)} and gates itself on the relevant API key (e.g.
 * {@code @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")}); the test runs against the real
 * endpoint to exercise the full HTTPS / HTTP/2 / real-network-pacing stack, where blocking calls often hide that a
 * plain-HTTP mock would not reach. It is parameterized over {@code logRequests}/{@code logResponses} so the logging
 * wrapper's separate code path is policed too.
 * <p>
 * BlockHound is JVM-global, so recorded violations are shared state; {@link #resetViolations()} clears them before
 * each test to keep them order-independent (they must run sequentially).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractStreamingChatModelPublisherNonBlockingIT {

    /** Blocking calls BlockHound observed on a policed thread. Cleared before each test by {@link #resetViolations()}. */
    private final List<Throwable> violations = new CopyOnWriteArrayList<>();

    /**
     * The reactive streaming model under test. {@code logging} toggles {@code logRequests}/{@code logResponses} so the
     * logging wrapper's code path is exercised too.
     */
    protected abstract StreamingChatModel newModel(boolean logging);

    /**
     * Name prefix of the transport's non-blocking worker threads that must never block. Defaults to the JDK HTTP
     * client's workers ({@code HttpClient-}); override for a transport that dispatches on differently-named threads.
     */
    protected String policedThreadNamePrefix() {
        return "HttpClient-";
    }

    /** The prompt used to drive a multi-token, multi-chunk response. Override if a provider needs a different one. */
    protected ChatRequest request(int n) {
        return ChatRequest.builder()
                .messages(UserMessage.from("Count from 1 to %s, one number per line.".formatted(n)))
                .build();
    }

    @BeforeEach
    void resetViolations() {
        violations.clear();
    }

    @BeforeAll
    void installBlockHound() {
        BlockHound.builder()
                // The bulk of a streamed response is parsed and dispatched on the transport's worker threads. If we
                // block any of these, throughput collapses under concurrency. BlockHound enforces this. (Stream
                // startup may run on ForkJoinPool.commonPool, which cannot be policed and is out of scope here.)
                .nonBlockingThreadPredicate(prev -> prev.or(t -> t.getName().startsWith(policedThreadNamePrefix())))
                // Pool bookkeeping, not application blocking: idle workers park on the work queue (getTask), exiting
                // workers acquire the pool's lock to coordinate shutdown (processWorkerExit).
                .allowBlockingCallsInside("java.util.concurrent.ThreadPoolExecutor", "getTask")
                .allowBlockingCallsInside("java.util.concurrent.ThreadPoolExecutor", "processWorkerExit")
                // Async test logging (logging=true): the logging HTTP client logs each streamed event on the worker
                // thread, and tinylog hands each entry to its writer thread under a monitor (WritingThread.add →
                // Object.notify()). The worker can briefly contend on that monitor and park — the logging backend's
                // internal handoff, not our pipeline. Tolerate it so logging=true doesn't flake.
                .allowBlockingCallsInside("org.tinylog.core.WritingThread", "add")
                // Record (don't throw): a thrown error on a worker thread kills the thread but never reaches our
                // subscriber, so the test would pass despite the violation. Recording lets us assert on it afterwards.
                .blockingMethodCallback(method -> violations.add(new BlockingOperationError(method)))
                .install();
    }

    @BeforeAll
    void warmUp() throws Exception {
        // The first real request triggers one-time, JVM-global lazy I/O on the worker threads (TLS truststore read
        // during the HTTPS handshake, class/JAR loading). Trigger it once so the measured tests see only steady-state
        // behavior; logging is enabled so the logging path's classes load here too. Any violations recorded here are
        // wiped by resetViolations() before the first test.
        awaitStream(newModel(true), request(10));
    }

    @ParameterizedTest(name = "logging={0}")
    @ValueSource(booleans = {false, true})
    void publisher_path_does_not_block_worker_threads(boolean logging) throws Exception {
        // Given: the real streaming endpoint and a multi-token response that exercises the pipeline across many chunks.
        StreamCapture capture = awaitStream(newModel(logging), request(50));

        // Then: stream completed normally, real events arrived, and no blocking call was detected on the worker threads.
        assertThat(capture.error()).isNull();
        assertThat(capture.received()).isNotEmpty();

        // Non-vacuity guard: at least one event must be delivered on a policed worker thread, so the empty-violations
        // assertion below isn't vacuous. The transport may deliver the first, already-buffered chunk on an unpoliced
        // pool, but a multi-token response guarantees later chunks land on policed workers.
        assertThat(capture.deliveryThreads()).anyMatch(name -> name.startsWith(policedThreadNamePrefix()));

        assertThat(violations).isEmpty();
    }

    /**
     * Sanity-checks the harness itself: a blocking call on a policed thread MUST be recorded. Together with the
     * delivery-thread assertion above — which proves the real pipeline runs on such threads — this guarantees
     * {@code publisher_path_does_not_block_worker_threads} cannot pass vacuously. If BlockHound ever stopped policing
     * the worker threads (wrong predicate, pipeline moved to an unpoliced pool, install failing, …), this would fail.
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
                .as("BlockHound must flag a blocking call on a policed streaming thread")
                .isNotEmpty();
    }

    private StreamCapture awaitStream(StreamingChatModel model, ChatRequest request) throws Exception {
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
