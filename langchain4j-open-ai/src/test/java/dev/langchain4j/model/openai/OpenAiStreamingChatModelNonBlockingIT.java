package dev.langchain4j.model.openai;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.StreamingEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.blockhound.BlockHound;
import reactor.blockhound.BlockingOperationError;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link OpenAiStreamingChatModel#chat(ChatRequest)}'s publisher path is fully
 * non-blocking on the JDK HTTP client's I/O threads. Uses BlockHound, which throws
 * {@link reactor.blockhound.BlockingOperationError} if any registered "non-blocking" thread
 * performs a blocking call (Socket read, InputStream read, Thread.sleep, Object.wait, etc.).
 * <p>
 * Runs against the real OpenAI endpoint to exercise the full HTTPS / HTTP/2 / real-network-pacing
 * stack — these code paths often hide blocking calls that WireMock-based plain HTTP wouldn't reach.
 * <p>
 * Parameterized over {@code logRequests}/{@code logResponses}: with logging enabled, the pipeline
 * goes through {@link dev.langchain4j.http.client.log.LoggingHttpClient}'s wrapping subscriber,
 * which is a separate code path that must also stay non-blocking.
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiStreamingChatModelNonBlockingIT {

    // TODO similar test for http client and AI Service

    /**
     * Recorded BlockHound violations. Cleared per test in {@link #resetViolations()}; asserted
     * empty after each test. Using a list (not a flag) so debugging can show all offending calls.
     */
    private static final List<Throwable> violations = new CopyOnWriteArrayList<>();

    @BeforeAll
    static void installBlockHound() {
        BlockHound.builder()
                // The JDK HTTP client threads are where our publisher pipeline runs. If we block
                // any of these, throughput collapses under concurrency. BlockHound enforces this.
                .nonBlockingThreadPredicate(prev -> prev.or(t -> t.getName().startsWith("HttpClient-")))
                // Pool bookkeeping, not application blocking: idle workers park on the work queue
                // (getTask), exiting workers acquire the pool's lock to coordinate shutdown
                // (processWorkerExit).
                .allowBlockingCallsInside("java.util.concurrent.ThreadPoolExecutor", "getTask")
                .allowBlockingCallsInside("java.util.concurrent.ThreadPoolExecutor", "processWorkerExit")
                // Replace the default throw-on-violation: a thrown error on a worker thread kills
                // the thread but doesn't reach our subscriber, so the test would pass despite the
                // violation. Recording it lets us assert on it after the stream completes.
                .blockingMethodCallback(method -> violations.add(new BlockingOperationError(method)))
                .install();
    }

    @BeforeEach
    void resetViolations() {
        violations.clear();
    }

    @ParameterizedTest(name = "logging={0}")
    @ValueSource(booleans = {false, true})
    void publisher_path_does_not_block_jdk_http_threads(boolean logging) throws Exception {
        // Given: real OpenAI streaming endpoint, short response to keep the call cheap.
        OpenAiStreamingChatModel model = OpenAiStreamingChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_4_O_MINI)
                .maxCompletionTokens(20)
                .logRequests(logging)
                .logResponses(logging)
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Say hello in one word."))
                .build();

        // When
        Flow.Publisher<StreamingEvent> publisher = model.chat(request);
        List<StreamingEvent> received = new CopyOnWriteArrayList<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        CompletableFuture<Void> done = new CompletableFuture<>();

        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(StreamingEvent event) {
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

        // Then: stream completed normally, real events arrived, and no blocking call was
        // detected on a JDK HTTP thread anywhere in the pipeline.
        assertThat(error.get()).as("subscriber received an error (logging=%s)", logging).isNull();
        assertThat(received).as("no events received (logging=%s)", logging).isNotEmpty();
        assertThat(violations)
                .as("BlockHound detected blocking calls on JDK HTTP threads (logging=%s) — see stack(s) below", logging)
                .isEmpty();
    }
}
