package dev.langchain4j.model.bedrock;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.UserMessage;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.blockhound.BlockHound;
import reactor.blockhound.BlockingOperationError;

/**
 * Verifies that Bedrock's asynchronous and reactive chat paths are genuinely non-blocking against the real AWS Bedrock
 * endpoint - the Bedrock counterpart of {@code AbstractChatModelNonBlockingIT} (which OpenAI and Anthropic share).
 * Bedrock runs on its own SDK (AWS SDK v2) rather than our {@code HttpClient}, and its two paths dispatch our pipeline
 * on two different SDK thread pools, both policed here by BlockHound:
 * <ul>
 *     <li>{@link BedrockChatModel#chatAsync(ChatRequest)} - the response future completes, and our
 *         {@code ConverseResponse -> ChatResponse} parse runs, on the SDK's async-response executor
 *         ({@code sdk-async-response-*}); and</li>
 *     <li>{@link BedrockStreamingChatModel#chat(ChatRequest)} - each {@code converseStream} event is parsed and
 *         dispatched on the SDK's Netty event-loop threads ({@code aws-java-sdk-NettyEventLoop-*}), where the SDK
 *         delivers them synchronously.</li>
 * </ul>
 * The Netty event loop reads the socket with the SDK's own non-blocking NIO (native, not flagged by BlockHound); it is
 * policed here only because our streaming pipeline runs on it, so blocking there would stall the SDK's I/O. (The
 * chatAsync response body is also read on the event loop but parsed on the async-response executor, so that path
 * polices the executor instead.)
 * <p>
 * Both paths live in one class on purpose: BlockHound is JVM-global and {@code install()} is once-per-JVM, so two
 * BlockHound test classes sharing a fork would leave the second one's violation tracking wired to the first's callback.
 * A single install here - policing both pools - keeps that tracking correct; the self-test proves it.
 * <p>
 * BlockHound violations are shared static state, cleared before each test by {@link #resetViolations()} (tests run
 * sequentially).
 */
@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
class BedrockNonBlockingIT {

    private static final String ASYNC_RESPONSE_THREAD_PREFIX = "sdk-async-response";
    private static final String EVENT_LOOP_THREAD_PREFIX = "aws-java-sdk-NettyEventLoop";

    /** Blocking calls BlockHound observed on a policed thread. Cleared before each test by {@link #resetViolations()}. */
    private static final List<Throwable> violations = new CopyOnWriteArrayList<>();

    @BeforeAll
    static void installBlockHound() {
        BlockHound.builder()
                // Our pipeline runs on two SDK pools: chatAsync's parse completes on the async-response executor, and
                // each streamed event's parse/dispatch runs on the Netty event loop. Blocking either collapses
                // throughput under concurrency, so both are policed. (The event loop's own NIO reads are native and not
                // flagged by BlockHound.)
                .nonBlockingThreadPredicate(prev -> prev.or(t -> t.getName().startsWith(ASYNC_RESPONSE_THREAD_PREFIX)
                        || t.getName().startsWith(EVENT_LOOP_THREAD_PREFIX)))
                // Pool bookkeeping, not application blocking: idle workers park on the work queue (getTask), exiting
                // workers coordinate shutdown (processWorkerExit).
                .allowBlockingCallsInside("java.util.concurrent.ThreadPoolExecutor", "getTask")
                .allowBlockingCallsInside("java.util.concurrent.ThreadPoolExecutor", "processWorkerExit")
                // Async test logging (logging=true): tinylog hands each entry to its writer thread under a monitor
                // (WritingThread.add -> Object.notify()); the worker can briefly park on that handoff - the logging
                // backend's internals, not our pipeline. Tolerate it so logging=true doesn't flake.
                .allowBlockingCallsInside("org.tinylog.core.WritingThread", "add")
                // Record (don't throw): a thrown error on a worker thread kills the thread but never reaches our
                // future/subscriber, so the test could pass despite the violation. Recording lets us assert on it.
                .blockingMethodCallback(method -> violations.add(new BlockingOperationError(method)))
                .install();
    }

    @BeforeAll
    static void warmUp() throws Exception {
        // First request/stream triggers one-time lazy work (TLS handshake, class/JAR loading, lazy async-client
        // creation) on the SDK threads. Do it once so the measured tests see only steady-state behavior; logging is on
        // so the logging path's classes load here too. Violations recorded here are wiped before each test.
        newChatModel().chatAsync(request()).get(60, TimeUnit.SECONDS);
        awaitStream(newStreamingModel(true));
    }

    @BeforeEach
    void resetViolations() {
        violations.clear();
    }

    @Test
    void chatAsync_does_not_block_the_caller_or_the_sdk_response_threads() throws Exception {
        Thread callerThread = Thread.currentThread();
        AtomicReference<Thread> completionThread = new AtomicReference<>();

        // when
        ChatResponse response = newChatModel()
                .chatAsync(request())
                .whenComplete((chatResponse, throwable) -> completionThread.set(Thread.currentThread()))
                .get(60, TimeUnit.SECONDS);

        // then: a real response arrived...
        assertThat(response.aiMessage().text()).isNotBlank();
        // ...delivered asynchronously on a background thread, so the caller was never blocked...
        assertThat(completionThread.get())
                .as("the response must be delivered off the calling thread")
                .isNotNull()
                .isNotEqualTo(callerThread);
        // ...and no blocking call happened on the AWS SDK response-completion threads.
        assertThat(violations)
                .as("BlockHound detected blocking on the AWS SDK response threads - see stack(s) below")
                .isEmpty();
    }

    @ParameterizedTest(name = "logging={0}")
    @ValueSource(booleans = {false, true})
    void streaming_publisher_does_not_block_the_event_loop_threads(boolean logging) throws Exception {
        // Given: the real streaming endpoint and a multi-token response that exercises the pipeline across many chunks.
        StreamCapture capture = awaitStream(newStreamingModel(logging));

        // Then: stream completed normally, real events arrived, and no blocking call was detected on the event loop.
        assertThat(capture.error()).isNull();
        assertThat(capture.received()).isNotEmpty();
        // Non-vacuity guard: at least one event must be delivered on a policed event-loop thread, so the
        // empty-violations assertion below isn't vacuous.
        assertThat(capture.deliveryThreads())
                .as("at least one event must be delivered on a policed event-loop thread; delivered on: %s",
                        capture.deliveryThreads())
                .anyMatch(name -> name.startsWith(EVENT_LOOP_THREAD_PREFIX));
        assertThat(violations)
                .as("BlockHound detected blocking on the AWS SDK event-loop threads (logging=%s) - see stack(s) below",
                        logging)
                .isEmpty();
    }

    /**
     * Sanity-checks the harness itself: a blocking call on either policed pool MUST be recorded, so the tests above
     * cannot pass vacuously if BlockHound ever stopped policing those threads (wrong predicate, install failing, ...).
     */
    @ParameterizedTest(name = "prefix={0}")
    @ValueSource(strings = {ASYNC_RESPONSE_THREAD_PREFIX, EVENT_LOOP_THREAD_PREFIX})
    void blockHound_detects_blocking_on_a_policed_thread(String policedPrefix) throws Exception {
        Thread thread = new Thread(
                () -> {
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                },
                policedPrefix + "-selftest");
        thread.start();
        thread.join(TimeUnit.SECONDS.toMillis(5));

        assertThat(violations)
                .as("BlockHound must flag a blocking call on a policed thread (%s)", policedPrefix)
                .isNotEmpty();
    }

    private static BedrockChatModel newChatModel() {
        return BedrockChatModel.builder().modelId("us.amazon.nova-lite-v1:0").build();
    }

    private static BedrockStreamingChatModel newStreamingModel(boolean logging) {
        return BedrockStreamingChatModel.builder()
                .modelId("us.amazon.nova-lite-v1:0")
                .logRequests(logging)
                .logResponses(logging)
                .build();
    }

    private static ChatRequest request() {
        return ChatRequest.builder()
                .messages(UserMessage.from("What is the capital of Germany?"))
                .build();
    }

    private static ChatRequest streamRequest() {
        return ChatRequest.builder()
                .messages(UserMessage.from("Count from 1 to 50, one number per line."))
                .build();
    }

    private static StreamCapture awaitStream(StreamingChatModel model) throws Exception {
        Flow.Publisher<StreamingEvent> publisher = model.chat(streamRequest());
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
