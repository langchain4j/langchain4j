package dev.langchain4j.reactive.streaming;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingEvent;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
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
 * Shared contract test (TCK) verifying that a provider's chat models are genuinely non-blocking on the JDK HTTP
 * client's worker threads (named {@code HttpClient-*}), where the response body is read, parsed and dispatched —
 * policed by BlockHound. Two paths are covered:
 * <ul>
 *     <li>{@link ChatModel#chatAsync(ChatRequest)} (single response) — delivered off the caller thread and parsed
 *         without blocking a worker; and</li>
 *     <li>{@link StreamingChatModel#chat(ChatRequest)} (reactive stream) — each SSE chunk parsed and dispatched
 *         without blocking a worker.</li>
 * </ul>
 * The endpoint is a local WireMock server returning the provider-supplied responses over <b>plain HTTP</b> — no TLS,
 * no real endpoint, no API key — so only the provider's pipeline is policed, not the JDK's HTTPS connection setup
 * (whose one-time truststore/class-loading file reads would otherwise be false positives on the worker threads).
 * <p>
 * A subclass provides the models pointed at the given base URL ({@link #syncModel}, {@link #streamingModel}) and the
 * provider-format response bodies ({@link #nonStreamingResponseBody()}, {@link #streamingResponseBody()}). Requests to
 * {@code /sync/...} get the non-streaming body; requests to {@code /stream/...} get the streaming SSE body.
 * <p>
 * Both paths live in one class on purpose: BlockHound is JVM-global and {@code install()} is once-per-JVM, so two
 * BlockHound test classes sharing a fork would leave the second one's violation tracking wired to the first's
 * callback. A single install here keeps that tracking correct — the self-test proves it.
 * <p>
 * This TCK requires the JDK {@code HttpClient} transport (the default for HTTP-based providers). A provider on a
 * different transport that cannot be driven by a plain-HTTP mock (e.g. Bedrock over the AWS SDK) cannot use it.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractChatModelNonBlockingIT {

    /** Blocking calls BlockHound observed on a policed thread. Cleared before each test by {@link #resetViolations()}. */
    private static final List<Throwable> violations = new CopyOnWriteArrayList<>();

    private WireMockServer wireMock;

    /** Builds the (single-response) chat model pointed at {@code baseUrl}; {@code logging} toggles request/response logging. */
    protected abstract ChatModel syncModel(String baseUrl, boolean logging);

    /** Builds the streaming chat model pointed at {@code baseUrl}; {@code logging} toggles request/response logging. */
    protected abstract StreamingChatModel streamingModel(String baseUrl, boolean logging);

    /** The provider's non-streaming chat response body (JSON) returned for a {@code /sync/...} request. */
    protected abstract String nonStreamingResponseBody();

    /** The provider's streaming SSE body returned for a {@code /stream/...} request. */
    protected abstract String streamingResponseBody();

    /** Name prefix of the transport threads that deliver body chunks. Defaults to the JDK HTTP client's workers. */
    protected String policedThreadNamePrefix() {
        return "HttpClient-";
    }

    @BeforeAll
    void installBlockHound() {
        BlockHound.builder()
                .nonBlockingThreadPredicate(prev -> prev.or(t -> t.getName().startsWith(policedThreadNamePrefix())))
                // Pool bookkeeping, not application blocking: idle workers park on the work queue (getTask), exiting
                // workers acquire the pool's lock to coordinate shutdown (processWorkerExit).
                .allowBlockingCallsInside("java.util.concurrent.ThreadPoolExecutor", "getTask")
                .allowBlockingCallsInside("java.util.concurrent.ThreadPoolExecutor", "processWorkerExit")
                // Async test logging (logging=true): tinylog hands each entry to its writer thread under a monitor
                // (WritingThread.add → Object.notify()); the worker can briefly park on that handoff — the logging
                // backend's internals, not our pipeline. Tolerate it so logging=true doesn't flake.
                .allowBlockingCallsInside("org.tinylog.core.WritingThread", "add")
                // Record (don't throw): a thrown error on a worker thread kills the thread but never reaches our
                // subscriber/future, so the test would pass despite the violation. Recording lets us assert on it.
                .blockingMethodCallback(method -> violations.add(new BlockingOperationError(method)))
                .install();
    }

    @BeforeAll
    void startServerAndWarmUp() throws Exception {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();

        wireMock.stubFor(post(urlPathMatching("/sync/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(nonStreamingResponseBody())));

        wireMock.stubFor(post(urlPathMatching("/stream/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/event-stream")
                        // Dribble the body out in chunks so it arrives in several reads on the worker threads,
                        // exercising the parse/dispatch pipeline repeatedly (not one buffered read).
                        .withChunkedDribbleDelay(10, 200)
                        .withBody(streamingResponseBody())));

        // The first request lazily loads client/parse classes on the worker threads (FileInputStream reading .class
        // from jars). Trigger it once so the measured tests see only steady-state behavior; violations are wiped by
        // resetViolations() before each test.
        syncModel(syncBaseUrl(), true).chatAsync(request()).get(10, TimeUnit.SECONDS);
        awaitStream(streamingModel(streamBaseUrl(), true));
    }

    @AfterAll
    void stopServer() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    @BeforeEach
    void resetViolations() {
        violations.clear();
    }

    private String syncBaseUrl() {
        return "http://localhost:" + wireMock.port() + "/sync/v1";
    }

    private String streamBaseUrl() {
        return "http://localhost:" + wireMock.port() + "/stream/v1";
    }

    @ParameterizedTest(name = "logging={0}")
    @ValueSource(booleans = {false, true})
    void chatAsync_does_not_block_the_http_worker_threads(boolean logging) throws Exception {
        // The response body is read and parsed on the worker threads (executeAsync); BlockHound proves nothing blocks
        // there. (Caller-not-blocked is not asserted: with a real, low-latency response it depends on a completion/attach
        // race under load; the worker-thread non-blocking below is the deterministic guarantee.)
        ChatResponse response = syncModel(syncBaseUrl(), logging).chatAsync(request()).get(10, TimeUnit.SECONDS);

        assertThat(response.aiMessage().text()).isNotBlank();
        assertThat(violations)
                .as("BlockHound detected blocking calls on the worker threads (logging=%s) — see stack(s) below", logging)
                .isEmpty();
    }

    @ParameterizedTest(name = "logging={0}")
    @ValueSource(booleans = {false, true})
    void streaming_publisher_does_not_block_the_http_worker_threads(boolean logging) throws Exception {
        Capture capture = awaitStream(streamingModel(streamBaseUrl(), logging));

        assertThat(capture.error).as("subscriber received an error (logging=%s)", logging).isNull();
        assertThat(capture.received).as("no events received (logging=%s)", logging).isNotEmpty();
        assertThat(capture.deliveryThreads)
                .as("at least one event must be delivered on a policed worker thread (logging=%s); delivered on: %s",
                        logging, capture.deliveryThreads)
                .anyMatch(name -> name.startsWith(policedThreadNamePrefix()));
        assertThat(violations)
                .as("BlockHound detected blocking calls on the worker threads (logging=%s) — see stack(s) below", logging)
                .isEmpty();
    }

    /**
     * Sanity-checks the harness itself: a blocking call on a policed thread MUST be recorded, so the tests above
     * cannot pass vacuously if BlockHound ever stopped policing the worker threads.
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
                .as("BlockHound must flag a blocking call on a policed thread")
                .isNotEmpty();
    }

    private Capture awaitStream(StreamingChatModel model) throws Exception {
        Flow.Publisher<StreamingEvent> publisher = model.chat(request());
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
        return new Capture(received, deliveryThreads, error.get());
    }

    private static ChatRequest request() {
        return ChatRequest.builder()
                .messages(UserMessage.from("What is the capital of Germany?"))
                .build();
    }

    private record Capture(List<StreamingEvent> received, Set<String> deliveryThreads, Throwable error) {}
}
