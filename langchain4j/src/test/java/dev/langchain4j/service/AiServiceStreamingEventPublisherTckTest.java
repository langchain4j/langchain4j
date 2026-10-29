package dev.langchain4j.service;

import static org.reactivestreams.FlowAdapters.toPublisher;

import java.util.concurrent.Flow;
import org.reactivestreams.Publisher;
import org.reactivestreams.tck.PublisherVerification;
import org.reactivestreams.tck.TestEnvironment;

/**
 * Reactive Streams TCK for the AI Service reactive publisher — the {@code Flow.Publisher<AiServiceStreamingEvent>}
 * that a reactive AI Service method returns and that Quarkus/Reactor consumers subscribe to. This is the layer where
 * the {@code onSubscribe}/cancellation contract lives, so the TCK guards it directly.
 * <p>
 * The publisher emits {@code N} {@link AiServiceStreamingEvent.PartialResponseEvent}s followed by exactly one
 * {@link AiServiceStreamingEvent.FinalResponseEvent}, so to produce exactly {@code elements} items the backing model
 * streams {@code elements - 1} partial responses. No chat memory is configured, so the cold stream is
 * re-subscribable (as the TCK requires).
 */
public class AiServiceStreamingEventPublisherTckTest extends PublisherVerification<AiServiceStreamingEvent> {

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

    interface EventStreamer {
        Flow.Publisher<AiServiceStreamingEvent> chat(String message);
    }

    public AiServiceStreamingEventPublisherTckTest() {
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
    public Publisher<AiServiceStreamingEvent> createPublisher(long elements) {
        int partialResponses = (int) Math.max(0, elements - 1); // + 1 FinalResponseEvent
        EventStreamer assistant = AiServices.builder(EventStreamer.class)
                .streamingChatModel(TckFixedCountStreamingChatModel.emitting(partialResponses))
                .build();
        return toPublisher(assistant.chat("hi"));
    }

    @Override
    public Publisher<AiServiceStreamingEvent> createFailedPublisher() {
        EventStreamer assistant = AiServices.builder(EventStreamer.class)
                .streamingChatModel(TckFixedCountStreamingChatModel.failing())
                .build();
        return toPublisher(assistant.chat("hi"));
    }
}
