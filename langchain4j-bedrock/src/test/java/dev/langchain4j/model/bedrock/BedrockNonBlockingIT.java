package dev.langchain4j.model.bedrock;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
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

    /**
     * Models are created once and reused across warm-up and the measured tests. A <em>fresh</em> AWS SDK client does
     * one-time TLS setup on its first request - reading the truststore/certificate files, which BlockHound flags as
     * {@code FileInputStream#readBytes} on the handshake/response threads. Reusing a warmed-up client moves that setup
     * into {@link #warmUp()} (outside the measured window, wiped by {@link #resetViolations()}) while the tested
     * request reuses the pooled, already-handshaked connection.
     */
    private static BedrockChatModel chatModel;

    private static BedrockStreamingChatModel streamingModelWithoutLogging;
    private static BedrockStreamingChatModel streamingModelWithLogging;

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
        chatModel = newChatModel();
        streamingModelWithoutLogging = newStreamingModel(false);
        streamingModelWithLogging = newStreamingModel(true);

        // Drive one request/stream through each client so all one-time lazy work (TLS handshake, truststore/certificate
        // reads, class/JAR loading, lazy async-client creation) happens here, on the SDK threads, before the measured
        // tests. The tested requests then reuse the pooled, already-handshaked connection. Both logging variants are
        // warmed so the logging path's classes load here too. Violations recorded here are wiped before each test.
        chatModel.chatAsync(request()).get(60, TimeUnit.SECONDS);
        awaitStream(streamingModelWithoutLogging);
        awaitStream(streamingModelWithLogging);
    }

    @BeforeEach
    void resetViolations() {
        violations.clear();
    }

    /**
     * BlockHound violations that indicate <em>our</em> pipeline blocked — everything except the AWS SDK's own one-time
     * setup I/O. Against the real endpoint the SDK does file reads on these threads that BlockHound flags but that are
     * not blocking we introduced: the TLS handshake reads the truststore/certificates and classes load lazily, all
     * surfacing as {@code FileInputStream#readBytes}. Reusing the warmed-up client removes these on the pooled
     * {@code chatAsync} connection, but the AWS SDK's event-stream streaming path re-does per-stream TLS setup on the
     * event loop, so they are unavoidable there. Our reactive parse/dispatch never touches the filesystem, so a file
     * read on these threads is always the SDK's own setup — exclude it, while still catching any real blocking we could
     * introduce (socket reads, {@code Thread.sleep}, {@code Object.wait}, lock parks).
     */
    private static List<Throwable> pipelineBlockingViolations() {
        return violations.stream()
                .filter(v -> !String.valueOf(v.getMessage()).contains("FileInputStream"))
                .toList();
    }

    @Test
    void chatAsync_does_not_block_the_caller_or_the_sdk_response_threads() throws Exception {
        Thread callerThread = Thread.currentThread();
        AtomicReference<Thread> completionThread = new AtomicReference<>();

        // when
        ChatResponse response = chatModel
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
        // ...and our pipeline performed no blocking call on the AWS SDK response-completion threads.
        assertThat(pipelineBlockingViolations())
                .as("BlockHound detected blocking on the AWS SDK response threads - see stack(s) below")
                .isEmpty();
    }

    @ParameterizedTest(name = "logging={0}")
    @ValueSource(booleans = {false, true})
    void streaming_publisher_does_not_block_the_event_loop_threads(boolean logging) throws Exception {
        // Given: the real streaming endpoint and a multi-token response that exercises the pipeline across many chunks.
        StreamCapture capture = awaitStream(logging ? streamingModelWithLogging : streamingModelWithoutLogging);

        // Then: stream completed normally, real events arrived, and no blocking call was detected on the event loop.
        assertThat(capture.error()).isNull();
        assertThat(capture.received()).isNotEmpty();
        // Non-vacuity guard: at least one event must be delivered on a policed event-loop thread, so the
        // empty-violations assertion below isn't vacuous.
        assertThat(capture.deliveryThreads())
                .as("at least one event must be delivered on a policed event-loop thread; delivered on: %s",
                        capture.deliveryThreads())
                .anyMatch(name -> name.startsWith(EVENT_LOOP_THREAD_PREFIX));
        assertThat(pipelineBlockingViolations())
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
        Flow.Publisher<ChatModelStreamingEvent> publisher = model.chat(streamRequest());
        List<ChatModelStreamingEvent> received = new CopyOnWriteArrayList<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        Set<String> deliveryThreads = ConcurrentHashMap.newKeySet();
        CompletableFuture<Void> done = new CompletableFuture<>();

        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ChatModelStreamingEvent event) {
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

    private record StreamCapture(List<ChatModelStreamingEvent> received, Set<String> deliveryThreads, Throwable error) {}
}
