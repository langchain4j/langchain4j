package dev.langchain4j.reactive.streaming;

import dev.langchain4j.Internal;
import dev.langchain4j.http.client.sse.HttpStreamingEvent;
import dev.langchain4j.model.chat.response.StreamingEvent;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.function.Function;
import java.util.function.Supplier;
import mutiny.zero.BackpressureStrategy;
import mutiny.zero.Tube;
import mutiny.zero.TubeConfiguration;
import mutiny.zero.ZeroPublisher;

/**
 * Builds a reactive {@code Publisher<StreamingEvent>} for a streaming chat model from an upstream
 * {@code Publisher<HttpStreamingEvent>} (typically {@code httpClient.stream(request, parser)}), factoring out the
 * boilerplate every provider's reactive path otherwise hand-rolls:
 * <ul>
 *   <li>a bounded, back-pressured {@link Tube} of {@link StreamingEvent}s ({@code ZeroPublisher});</li>
 *   <li>a subscriber whose cancellation is <b>subscription-based</b> — it cancels the upstream
 *       {@code Flow.Subscription} on <b>any</b> terminal signal (a downstream cancel, an error, or a buffer overflow),
 *       so, for example, an overflow actually aborts the HTTP connection; and</li>
 *   <li>delegation of each upstream event to a provider-supplied {@link Sink}.</li>
 * </ul>
 * Only the per-event interpretation (parsing the provider's SSE payloads and driving a
 * {@code StreamingChatResponseHandler} / {@link TubeBackedStreamingChatResponseHandler}) is provider-specific; that
 * lives in the {@link Sink}, created per subscription from the {@link Tube}.
 */
@Internal
public final class HttpStreamingChatPublisher {

    private HttpStreamingChatPublisher() {}

    /**
     * Consumes the upstream {@link HttpStreamingEvent}s of a single subscription and drives the assembled
     * {@link StreamingEvent}s into the {@link Tube} (typically via a {@link TubeBackedStreamingChatResponseHandler}).
     * Provider-specific.
     */
    public interface Sink {

        /** Handle one upstream event (e.g. an {@code HttpResponseReceived} or a {@code ServerSentEvent}). */
        void onEvent(HttpStreamingEvent event);

        /** The upstream stream failed. */
        void onError(Throwable error);

        /** The upstream stream completed; finalize (build and emit the response, complete the tube). */
        void onComplete();
    }

    /**
     * @param bufferSize  size of the bounded back-pressure buffer
     * @param upstream    supplies the upstream HTTP event publisher (subscribed once per downstream subscription)
     * @param sinkFactory creates the per-subscription {@link Sink} from the subscription's {@link Tube}
     */
    public static Publisher<StreamingEvent> create(
            int bufferSize,
            Supplier<Publisher<HttpStreamingEvent>> upstream,
            Function<Tube<StreamingEvent>, Sink> sinkFactory) {

        TubeConfiguration config = new TubeConfiguration()
                .withBackpressureStrategy(BackpressureStrategy.BUFFER)
                .withBufferSize(bufferSize);

        return ZeroPublisher.create(config, tube -> {
            Sink sink = sinkFactory.apply(tube);
            upstream.get().subscribe(new Subscriber<>() {

                @Override
                public void onSubscribe(Subscription subscription) {
                    if (tube.cancelled()) {
                        subscription.cancel();
                        return;
                    }
                    // whenTerminates (not whenCancelled): abort the upstream HTTP stream on ANY terminal signal -
                    // downstream cancel, an error, or a buffer overflow - so overflow actually aborts the connection.
                    tube.whenTerminates(subscription::cancel);
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(HttpStreamingEvent item) {
                    sink.onEvent(item);
                }

                @Override
                public void onError(Throwable throwable) {
                    sink.onError(throwable);
                }

                @Override
                public void onComplete() {
                    sink.onComplete();
                }
            });
        });
    }
}
