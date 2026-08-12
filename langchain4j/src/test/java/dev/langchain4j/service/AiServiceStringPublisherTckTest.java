package dev.langchain4j.service;

import static org.reactivestreams.FlowAdapters.toPublisher;

import java.util.concurrent.Flow;
import org.reactivestreams.Publisher;
import org.reactivestreams.tck.PublisherVerification;
import org.reactivestreams.tck.TestEnvironment;

/**
 * Reactive Streams TCK for the text-only AI Service publisher — the {@code Flow.Publisher<String>} produced by
 * {@code AiServiceStreamingEventPublisher.toTextPublisher(...)} (also used to back {@code Multi<String>} /
 * {@code Flux<String>} via the {@code PublisherAdapter} SPI).
 * <p>
 * With no output guardrails the text of each partial response is emitted and the terminal response contributes no
 * item, so a model streaming {@code elements} partial responses yields exactly {@code elements} strings. No chat
 * memory is configured, so the cold stream is re-subscribable (as the TCK requires).
 */
public class AiServiceStringPublisherTckTest extends PublisherVerification<String> {

    // Each subscription spins up a full AI Service invocation (proxy dispatch, executor and SPI initialisation, a
    // fresh model-delivery thread) before the first item can be emitted, so the budget for *expected* signals must
    // accommodate cold-start latency on a loaded CI runner. This timeout only adds slack: fast signals return
    // immediately, so passing tests are unaffected and only genuinely-stuck publishers ever wait this long.
    private static final long DEFAULT_TIMEOUT_MILLIS = 10_000L;
    // Kept tight, independent of the receive timeout, so the "no signal must arrive" assertions stay fast.
    private static final long DEFAULT_NO_SIGNALS_TIMEOUT_MILLIS = 2_000L;
    private static final long DEFAULT_POLL_TIMEOUT_MILLIS = 50L;
    private static final long PUBLISHER_REFERENCE_CLEANUP_TIMEOUT_MILLIS = 300L;
    private static final long MAX_ELEMENTS = 100L;

    interface StringStreamer {
        Flow.Publisher<String> chat(String message);
    }

    public AiServiceStringPublisherTckTest() {
        super(
                new TestEnvironment(
                        DEFAULT_TIMEOUT_MILLIS, DEFAULT_NO_SIGNALS_TIMEOUT_MILLIS, DEFAULT_POLL_TIMEOUT_MILLIS),
                PUBLISHER_REFERENCE_CLEANUP_TIMEOUT_MILLIS);
    }

    @Override
    public long maxElementsFromPublisher() {
        return MAX_ELEMENTS;
    }

    @Override
    public Publisher<String> createPublisher(long elements) {
        StringStreamer assistant = AiServices.builder(StringStreamer.class)
                .streamingChatModel(TckFixedCountStreamingChatModel.emitting((int) elements))
                .build();
        return toPublisher(assistant.chat("hi"));
    }

    @Override
    public Publisher<String> createFailedPublisher() {
        StringStreamer assistant = AiServices.builder(StringStreamer.class)
                .streamingChatModel(TckFixedCountStreamingChatModel.failing())
                .build();
        return toPublisher(assistant.chat("hi"));
    }
}
