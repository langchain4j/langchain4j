package dev.langchain4j.reactive.streaming;

import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
import dev.langchain4j.model.chat.response.PartialResponse;
import java.util.List;
import java.util.concurrent.Flow;
import org.reactivestreams.tck.TestEnvironment;
import reactor.blockhound.BlockHound;
import reactor.blockhound.BlockingOperationError;

/**
 * Shared helpers for the reactive tests of the streaming chat models. Each one encodes a <b>policy</b> that has to
 * be identical everywhere it is applied, which is why it lives here instead of being repeated per provider: a copy
 * that drifts turns into a test that passes for the wrong reason.
 */
public final class ReactiveStreamingTestSupport {

    /**
     * Budget for the signals a TCK test <b>expects</b>. Every subscription performs real work - an HTTP round-trip,
     * or a scheduled replay - before the first item can be emitted, so this has to absorb cold-start latency on a
     * loaded CI runner. It only adds slack: prompt signals return immediately, so passing tests are unaffected and
     * only a genuinely stuck publisher ever waits this long.
     */
    public static final long TCK_TIMEOUT_MILLIS = 10_000L;

    /** Kept tight, independent of the receive timeout, so the "no signal must arrive" assertions stay fast. */
    public static final long TCK_NO_SIGNALS_TIMEOUT_MILLIS = 2_000L;

    private static final long TCK_POLL_TIMEOUT_MILLIS = 50L;

    /** Passed to {@code PublisherVerification}'s second constructor argument. */
    public static final long TCK_PUBLISHER_REFERENCE_CLEANUP_TIMEOUT_MILLIS = 300L;

    private ReactiveStreamingTestSupport() {}

    /** The {@link TestEnvironment} every publisher TCK in this project should run with. */
    public static TestEnvironment tckTestEnvironment() {
        return new TestEnvironment(TCK_TIMEOUT_MILLIS, TCK_NO_SIGNALS_TIMEOUT_MILLIS, TCK_POLL_TIMEOUT_MILLIS);
    }

    /**
     * A demand-preserving filter that forwards only {@link PartialResponse} events, dropping the provider framing
     * events and the terminal aggregated response. It hands the real upstream subscription straight to the
     * downstream subscriber, so demand and cancellation reach the model publisher directly and the publisher's
     * {@code onSubscribe} / demand / cancel / error / complete contract is still what the TCK exercises.
     * <p>
     * Providers relay framing events whose count the TCK cannot pin down, so this narrows the stream to the one
     * event type that maps one-to-one onto what the test asked the model to produce.
     */
    public static Flow.Publisher<ChatModelStreamingEvent> partialResponsesOnly(
            Flow.Publisher<ChatModelStreamingEvent> source) {
        return downstream -> source.subscribe(new Flow.Subscriber<>() {

            private Flow.Subscription subscription;

            @Override
            public void onSubscribe(Flow.Subscription s) {
                this.subscription = s;
                downstream.onSubscribe(s);
            }

            @Override
            public void onNext(ChatModelStreamingEvent event) {
                if (event instanceof PartialResponse) {
                    downstream.onNext(event);
                } else {
                    subscription.request(1); // dropped a non-text event; top up demand
                }
            }

            @Override
            public void onError(Throwable error) {
                downstream.onError(error);
            }

            @Override
            public void onComplete() {
                downstream.onComplete();
            }
        });
    }

    /**
     * A {@link BlockHound} builder that polices the given thread-name prefixes and records - rather than throws -
     * the blocking calls it observes.
     * <p>
     * Recording matters: an error thrown on a worker thread kills that thread but never reaches the subscriber or
     * the future, so a throwing configuration would let the test pass despite the violation. Collecting into
     * {@code violations} lets the test assert on it instead.
     * <p>
     * Callers may add their own exemptions before calling {@code install()}.
     *
     * @param violations                 the list each observed blocking call is recorded into
     * @param policedThreadNamePrefixes  thread-name prefixes that must never block
     */
    public static BlockHound.Builder blockHoundBuilder(
            List<Throwable> violations, String... policedThreadNamePrefixes) {
        return BlockHound.builder()
                .nonBlockingThreadPredicate(previous -> previous.or(thread -> {
                    for (String prefix : policedThreadNamePrefixes) {
                        if (thread.getName().startsWith(prefix)) {
                            return true;
                        }
                    }
                    return false;
                }))
                // Pool bookkeeping, not application blocking: idle workers park on the work queue (getTask), exiting
                // workers acquire the pool's lock to coordinate shutdown (processWorkerExit).
                .allowBlockingCallsInside("java.util.concurrent.ThreadPoolExecutor", "getTask")
                .allowBlockingCallsInside("java.util.concurrent.ThreadPoolExecutor", "processWorkerExit")
                // Async test logging (logging=true): tinylog hands each entry to its writer thread under a monitor
                // (WritingThread.add -> Object.notify()); the worker can briefly park on that handoff - the logging
                // backend's internals, not our pipeline. Tolerate it so logging=true does not flake.
                .allowBlockingCallsInside("org.tinylog.core.WritingThread", "add")
                .blockingMethodCallback(method -> violations.add(new BlockingOperationError(method)));
    }
}
