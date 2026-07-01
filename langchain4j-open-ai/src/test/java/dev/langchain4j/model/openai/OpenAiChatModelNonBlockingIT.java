package dev.langchain4j.model.openai;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.blockhound.BlockHound;
import reactor.blockhound.BlockingOperationError;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link OpenAiChatModel#chatAsync(ChatRequest)} is genuinely non-blocking:
 * <ul>
 *     <li>the calling thread is not pinned — the response is delivered asynchronously on a different
 *         (background) thread; and</li>
 *     <li>no blocking call is performed on the JDK HTTP client's worker threads (named {@code HttpClient-*}),
 *         where the response body is read — policed by BlockHound.</li>
 * </ul>
 * Counterpart of {@code OpenAiStreamingChatModelNonBlockingIT}, for the single-response (non-streaming)
 * async API. Runs against the real OpenAI endpoint to exercise the full HTTPS/HTTP-2 stack.
 * <p>
 * Parameterized over a {@code logging} flag: with logging on, the pipeline goes through
 * {@link dev.langchain4j.http.client.log.LoggingHttpClient}, whose response logging runs in the future's
 * completion callback (i.e. potentially on a policed worker thread) — a separate code path that must also
 * stay non-blocking. Test logging is asynchronous (see {@code writingthread} in {@code tinylog.properties}),
 * so log writes happen off the worker thread.
 * <p>
 * BlockHound is JVM-global, so recorded violations are shared static state; {@link #resetViolations()}
 * clears them before each test (tests run sequentially).
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiChatModelNonBlockingIT {

    /**
     * Blocking calls BlockHound observed on a policed thread. Cleared before each test by
     * {@link #resetViolations()}; the async test expects none, the self-test expects one.
     */
    private static final List<Throwable> violations = new CopyOnWriteArrayList<>();

    @BeforeAll
    static void installBlockHound() {
        BlockHound.builder()
                // The response body is read on the JDK HTTP client's worker threads ("HttpClient-*").
                // If we block any of these, throughput collapses under concurrency.
                .nonBlockingThreadPredicate(prev -> prev.or(t -> t.getName().startsWith("HttpClient-")))
                // Pool bookkeeping, not application blocking: idle workers park on the work queue (getTask),
                // exiting workers acquire the pool's lock to coordinate shutdown (processWorkerExit).
                .allowBlockingCallsInside("java.util.concurrent.ThreadPoolExecutor", "getTask")
                .allowBlockingCallsInside("java.util.concurrent.ThreadPoolExecutor", "processWorkerExit")
                // Async test logging (logging=true): LoggingHttpClient logs the response in the future's
                // completion callback, and tinylog hands each entry to its writer thread under a monitor
                // (WritingThread.add -> Object.notify()). The worker can briefly contend on that monitor
                // and park — the logging backend's internal handoff, not our pipeline. Tolerate it so the
                // logging path stays exercised without flaking. (Our pipeline is still fully policed.)
                .allowBlockingCallsInside("org.tinylog.core.WritingThread", "add")
                // Record (don't throw): a thrown error on a worker thread kills the thread but never reaches
                // our future, so the test could pass despite the violation. Recording lets us assert on it.
                .blockingMethodCallback(method -> violations.add(new BlockingOperationError(method)))
                .install();
    }

    @BeforeAll
    static void warmUp() throws Exception {
        // The first real request triggers one-time, JVM-global lazy I/O on the worker threads (TLS
        // truststore read during the HTTPS handshake, class/JAR loading). Trigger it once here so the
        // measured test sees only steady-state behavior; logging is enabled so the logging path's classes
        // load here too. Violations recorded here are wiped before the test.
        newModel(true).chatAsync(request()).get(30, TimeUnit.SECONDS);
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

        // when
        ChatResponse response = newModel(logging)
                .chatAsync(request())
                .whenComplete((chatResponse, throwable) -> completionThread.set(Thread.currentThread()))
                .get(30, TimeUnit.SECONDS);

        // then: a real response arrived...
        assertThat(response.aiMessage().text()).isNotBlank();
        // ...delivered asynchronously on a background thread, so the caller was never blocked...
        assertThat(completionThread.get())
                .as("the response must be delivered off the calling thread")
                .isNotNull()
                .isNotEqualTo(callerThread);
        // ...and no blocking call happened on the JDK HTTP worker threads.
        assertThat(violations)
                .as("BlockHound detected blocking calls on JDK HTTP worker threads — see stack(s) below")
                .isEmpty();
    }

    /**
     * Sanity-checks the harness itself: a blocking call on a policed ("HttpClient-*") thread MUST be
     * recorded. Guarantees the test above cannot pass vacuously if BlockHound ever stopped policing the
     * worker threads. Order-independent: {@link #resetViolations()} clears the list first, and the
     * blocking call records its own violation synchronously (the worker thread is joined before asserting).
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
                .as("BlockHound must flag a blocking call on a policed thread")
                .isNotEmpty();
    }

    private static OpenAiChatModel newModel(boolean logging) {
        return OpenAiChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_4_O_MINI)
                .maxTokens(20)
                .logRequests(logging)
                .logResponses(logging)
                .build();
    }

    private static ChatRequest request() {
        return ChatRequest.builder()
                .messages(UserMessage.from("What is the capital of Germany?"))
                .build();
    }
}
