package dev.langchain4j.model.openai;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import dev.langchain4j.data.message.UserMessage;
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
 * Verifies that the OpenAI models are genuinely non-blocking on the JDK HTTP client's worker threads
 * (named {@code HttpClient-*}), where the response body is read, parsed and dispatched — policed by BlockHound:
 * <ul>
 *     <li>{@link OpenAiChatModel#chatAsync(ChatRequest)} (single response) — delivered off the caller thread and
 *         parsed without blocking a worker; and</li>
 *     <li>{@link OpenAiStreamingChatModel#chat(ChatRequest)} (reactive stream) — each SSE chunk parsed and
 *         dispatched without blocking a worker.</li>
 * </ul>
 * The endpoint is a local WireMock server returning deterministic OpenAI-style responses over plain HTTP — no TLS,
 * no real endpoint, no API key — so only the OpenAI pipeline is policed, not the JDK's HTTPS connection setup (whose
 * one-time truststore/class-loading file reads would otherwise be false positives on the worker threads).
 * <p>
 * Both paths live in <b>one</b> test class on purpose: BlockHound is JVM-global and {@code install()} is once-per-JVM,
 * so two BlockHound test classes sharing a fork would leave the second one's violation tracking wired to the first's
 * callback. A single install here keeps that tracking correct. It is parameterized over logging so the
 * {@link dev.langchain4j.http.client.log.LoggingHttpClient} code path is policed too.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpenAiNonBlockingIT {

    /** Blocking calls BlockHound observed on a policed thread. Cleared before each test by {@link #resetViolations()}. */
    private static final List<Throwable> violations = new CopyOnWriteArrayList<>();

    private WireMockServer wireMock;

    @BeforeAll
    void installBlockHound() {
        BlockHound.builder()
                // The response body is read, parsed and dispatched on the JDK HTTP client's worker threads
                // ("HttpClient-*"). If we block any of these, throughput collapses under concurrency.
                .nonBlockingThreadPredicate(prev -> prev.or(t -> t.getName().startsWith("HttpClient-")))
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

        wireMock.stubFor(post("/sync/v1/chat/completions")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(chatCompletionJson("Berlin"))));

        wireMock.stubFor(post("/stream/v1/chat/completions")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/event-stream")
                        // Dribble the body out in chunks so it arrives in several reads on the worker threads,
                        // exercising the parse/dispatch pipeline repeatedly (not one buffered read).
                        .withChunkedDribbleDelay(10, 200)
                        .withBody(openAiStreamBody(20))));

        // The first request lazily loads client/parse classes on the worker threads (FileInputStream reading .class
        // from jars). Trigger it once so the measured tests see only steady-state behavior; violations are wiped by
        // resetViolations() before each test.
        syncModel(true).chatAsync(request()).get(10, TimeUnit.SECONDS);
        awaitStream(streamingModel(true));
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

    @ParameterizedTest(name = "logging={0}")
    @ValueSource(booleans = {false, true})
    void chatAsync_does_not_block_the_caller_or_the_http_worker_threads(boolean logging) throws Exception {
        Thread callerThread = Thread.currentThread();
        AtomicReference<Thread> completionThread = new AtomicReference<>();

        ChatResponse response = syncModel(logging)
                .chatAsync(request())
                .whenComplete((chatResponse, throwable) -> completionThread.set(Thread.currentThread()))
                .get(10, TimeUnit.SECONDS);

        assertThat(response.aiMessage().text()).isNotBlank();
        assertThat(completionThread.get())
                .as("the response must be delivered off the calling thread (logging=%s)", logging)
                .isNotNull()
                .isNotEqualTo(callerThread);
        assertThat(violations)
                .as("BlockHound detected blocking calls on JDK HTTP worker threads (logging=%s) — see stack(s) below",
                        logging)
                .isEmpty();
    }

    @ParameterizedTest(name = "logging={0}")
    @ValueSource(booleans = {false, true})
    void streaming_publisher_does_not_block_the_http_worker_threads(boolean logging) throws Exception {
        Capture capture = awaitStream(streamingModel(logging));

        assertThat(capture.error).as("subscriber received an error (logging=%s)", logging).isNull();
        assertThat(capture.received).as("no events received (logging=%s)", logging).isNotEmpty();
        // Non-vacuity: at least one event delivered on a policed worker thread.
        assertThat(capture.deliveryThreads)
                .as("at least one event must be delivered on a policed HttpClient-* thread (logging=%s); delivered on: %s",
                        logging, capture.deliveryThreads)
                .anyMatch(name -> name.startsWith("HttpClient-"));
        assertThat(violations)
                .as("BlockHound detected blocking calls on JDK HTTP worker threads (logging=%s) — see stack(s) below",
                        logging)
                .isEmpty();
    }

    /**
     * Sanity-checks the harness itself: a blocking call on a policed ("HttpClient-*") thread MUST be recorded, so the
     * tests above cannot pass vacuously if BlockHound ever stopped policing the worker threads.
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
                "HttpClient-selftest");
        thread.start();
        thread.join(TimeUnit.SECONDS.toMillis(5));

        assertThat(violations)
                .as("BlockHound must flag a blocking call on a policed thread")
                .isNotEmpty();
    }

    private OpenAiChatModel syncModel(boolean logging) {
        return OpenAiChatModel.builder()
                .baseUrl("http://localhost:" + wireMock.port() + "/sync/v1")
                .apiKey("test-key")
                .modelName("gpt-4o-mini")
                .logRequests(logging)
                .logResponses(logging)
                .build();
    }

    private OpenAiStreamingChatModel streamingModel(boolean logging) {
        return OpenAiStreamingChatModel.builder()
                .baseUrl("http://localhost:" + wireMock.port() + "/stream/v1")
                .apiKey("test-key")
                .modelName("gpt-4o-mini")
                .logRequests(logging)
                .logResponses(logging)
                .build();
    }

    private static ChatRequest request() {
        return ChatRequest.builder()
                .messages(UserMessage.from("What is the capital of Germany?"))
                .build();
    }

    private Capture awaitStream(OpenAiStreamingChatModel model) throws Exception {
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

    private record Capture(List<StreamingEvent> received, Set<String> deliveryThreads, Throwable error) {}

    private static String chatCompletionJson(String content) {
        return "{\"id\":\"x\",\"object\":\"chat.completion\",\"created\":1,\"model\":\"gpt-4o-mini\","
                + "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"" + content
                + "\"},\"finish_reason\":\"stop\"}],"
                + "\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}}";
    }

    private static String openAiStreamBody(int contentChunks) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < contentChunks; i++) {
            sb.append("data: ").append(contentChunk("chunk-" + i)).append("\n\n");
        }
        sb.append("data: [DONE]\n\n");
        return sb.toString();
    }

    private static String contentChunk(String content) {
        return "{\"id\":\"x\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"gpt-4o-mini\","
                + "\"choices\":[{\"index\":0,\"delta\":{\"content\":\"" + content + "\"},\"finish_reason\":null}]}";
    }
}
