package dev.langchain4j.model.bedrock;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.blockhound.BlockHound;
import reactor.blockhound.BlockingOperationError;

/**
 * Verifies that {@link BedrockChatModel#chatAsync(ChatRequest)} is genuinely non-blocking against the real AWS
 * Bedrock endpoint - the counterpart of {@code OpenAiChatModelNonBlockingIT}, for a provider on its <b>own SDK</b>
 * (AWS SDK v2) rather than our {@code HttpClient}:
 * <ul>
 *     <li>the calling thread is not pinned - the response is delivered on a background thread; and</li>
 *     <li>no blocking call is performed on the AWS SDK's async response-completion threads
 *         ({@code sdk-async-response-*}), where the response future completes and our
 *         {@code ConverseResponse -> ChatResponse} parse runs - policed by BlockHound.</li>
 * </ul>
 * The response body itself is read on the SDK's Netty event-loop threads
 * ({@code aws-java-sdk-NettyEventLoopGroup-*}); those are the SDK's own non-blocking I/O and are not policed here -
 * this IT polices the threads our pipeline runs on.
 * <p>
 * BlockHound is JVM-global; violations are shared static state, cleared before each test by
 * {@link #resetViolations()} (tests run sequentially).
 */
@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
class BedrockChatModelNonBlockingIT {

    /** Blocking calls BlockHound observed on a policed thread. Cleared before each test by {@link #resetViolations()}. */
    private static final List<Throwable> violations = new CopyOnWriteArrayList<>();

    @BeforeAll
    static void installBlockHound() {
        BlockHound.builder()
                // AWS SDK v2 completes the response future - where our parse runs - on its dedicated
                // "sdk-async-response-*" executor (deliberately off the Netty event loop). If we block there,
                // throughput collapses under concurrency.
                .nonBlockingThreadPredicate(prev -> prev.or(t -> t.getName().startsWith("sdk-async-response")))
                // Pool bookkeeping, not application blocking: idle workers park on the work queue (getTask),
                // exiting workers coordinate shutdown (processWorkerExit).
                .allowBlockingCallsInside("java.util.concurrent.ThreadPoolExecutor", "getTask")
                .allowBlockingCallsInside("java.util.concurrent.ThreadPoolExecutor", "processWorkerExit")
                // Record (don't throw): a thrown error on a worker thread kills the thread but never reaches our
                // future, so the test could pass despite the violation. Recording lets us assert on it.
                .blockingMethodCallback(method -> violations.add(new BlockingOperationError(method)))
                .install();
    }

    @BeforeAll
    static void warmUp() throws Exception {
        // First request triggers one-time lazy work (TLS handshake, class/JAR loading, lazy async-client creation).
        // Do it once so the measured test sees only steady-state behavior; violations recorded here are wiped before
        // the test.
        newModel().chatAsync(request()).get(60, TimeUnit.SECONDS);
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
        ChatResponse response = newModel()
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

    /**
     * Sanity-checks the harness itself: a blocking call on a policed ("sdk-async-response-*") thread MUST be
     * recorded, so the test above cannot pass vacuously if BlockHound ever stopped policing those threads.
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
                "sdk-async-response-selftest");
        thread.start();
        thread.join(TimeUnit.SECONDS.toMillis(5));

        assertThat(violations)
                .as("BlockHound must flag a blocking call on a policed thread")
                .isNotEmpty();
    }

    private static BedrockChatModel newModel() {
        return BedrockChatModel.builder().modelId("us.amazon.nova-lite-v1:0").build();
    }

    private static ChatRequest request() {
        return ChatRequest.builder()
                .messages(UserMessage.from("What is the capital of Germany?"))
                .build();
    }
}
