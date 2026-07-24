package dev.langchain4j.model.chat.common;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.StreamingEvent;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/**
 * Common tests for the {@link ChatModelListener} contract of the reactive publisher path
 * ({@link StreamingChatModel#chat(ChatRequest)} returning a {@code Publisher<StreamingEvent>}).
 * <p>
 * The listener wiring lives entirely in the shared {@code chat(ChatRequest)} default method, but each provider is
 * responsible for driving that pipeline correctly from its own transport (SSE over the {@code HttpClient}, a vendor
 * SDK's event stream, …). These tests pin the promised behavior for every provider:
 * <ul>
 *   <li>success — {@code onRequest} then {@code onResponse}, never {@code onError};</li>
 *   <li>provider/stream failure — {@code onRequest} then {@code onError}, never {@code onResponse};</li>
 *   <li>downstream cancels mid-stream — {@code onRequest} only;</li>
 *   <li>subscriber throws from {@code onNext} (Reactive Streams Rule 2.13 violation) — {@code onRequest} only; the
 *       stream is cancelled and no {@code onResponse}/{@code onError} fires (unlike the handler path, which routes
 *       handler exceptions to {@code onError} and keeps streaming).</li>
 * </ul>
 * <p>
 * A provider supplies {@link #createModel(ChatModelListener, StreamScenario)}: a model, with the listener attached,
 * whose publisher replays a controlled stream (deterministically, no network). Each provider builds that stream
 * with whatever mock its transport calls for — WireMock/local HTTP for SSE providers, a mocked async client for
 * SDK-based providers — but the assertions below are identical for all of them.
 */
public abstract class AbstractStreamingChatModelPublisherListenerTest {

    /**
     * How many events to cancel/throw after when a scenario streams incrementally.
     */
    protected static final int EVENTS_BEFORE_ACTION = 3;

    /**
     * Specifies the stream a {@link #createModel(ChatModelListener, StreamScenario)} publisher must replay.
     *
     * @param contentChunks   number of partial responses to emit before completing (ignored when {@code fail})
     * @param spreadOverMillis if {@code > 0}, spread the events over roughly this duration so a consumer can act
     *                         (cancel / throw) while the stream is still in progress; {@code 0} means "as fast as
     *                         possible"
     * @param fail            when {@code true}, fail the stream with a provider error instead of completing
     */
    protected record StreamScenario(int contentChunks, long spreadOverMillis, boolean fail) {

        public static StreamScenario success(int contentChunks) {
            return new StreamScenario(contentChunks, 0, false);
        }

        public static StreamScenario failure() {
            return new StreamScenario(0, 0, true);
        }

        public static StreamScenario dribbled(int contentChunks, long spreadOverMillis) {
            return new StreamScenario(contentChunks, spreadOverMillis, false);
        }
    }

    /**
     * Builds a model with {@code listener} attached whose publisher replays the given {@link StreamScenario} when
     * subscribed. Must not perform any real network call.
     */
    protected abstract StreamingChatModel createModel(ChatModelListener listener, StreamScenario scenario);

    @Test
    void listener_is_invoked_with_request_then_response_on_success() throws Exception {
        ChatModelListener listener = mock(ChatModelListener.class);
        StreamingChatModel model = createModel(listener, StreamScenario.success(3));

        subscribeAndAwaitTermination(model).get(10, SECONDS);

        InOrder inOrder = inOrder(listener);
        inOrder.verify(listener).onRequest(any());
        inOrder.verify(listener).onResponse(any());
        verify(listener, never()).onError(any());
        verifyNoMoreInteractions(listener);
    }

    @Test
    void listener_is_invoked_with_request_then_error_on_failure() throws Exception {
        ChatModelListener listener = mock(ChatModelListener.class);
        StreamingChatModel model = createModel(listener, StreamScenario.failure());

        subscribeAndAwaitTermination(model).get(10, SECONDS);

        InOrder inOrder = inOrder(listener);
        inOrder.verify(listener).onRequest(any());
        inOrder.verify(listener).onError(any());
        verify(listener, never()).onResponse(any());
        verifyNoMoreInteractions(listener);
    }

    @Test
    void listener_gets_only_onRequest_when_subscription_is_cancelled_mid_stream() throws Exception {
        ChatModelListener listener = mock(ChatModelListener.class);
        StreamingChatModel model = createModel(listener, StreamScenario.dribbled(50, 5_000));

        AtomicInteger events = new AtomicInteger();
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicReference<Flow.Subscription> subscriptionReference = new AtomicReference<>();
        CompletableFuture<Void> cancelledFuture = new CompletableFuture<>();

        model.chat(request()).subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscriptionReference.set(subscription);
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(StreamingEvent event) {
                if (cancelled.get()) {
                    return;
                }
                if (events.incrementAndGet() == EVENTS_BEFORE_ACTION) {
                    cancelled.set(true);
                    subscriptionReference.get().cancel();
                    cancelledFuture.complete(null);
                }
            }

            @Override
            public void onError(Throwable throwable) {}

            @Override
            public void onComplete() {}
        });

        cancelledFuture.get(10, SECONDS);
        Thread.sleep(3_000); // ensure no late onResponse/onError reaches the listener

        verify(listener).onRequest(any());
        verify(listener, never()).onResponse(any());
        verify(listener, never()).onError(any());
        verifyNoMoreInteractions(listener);
    }

    @Test
    void listener_gets_only_onRequest_when_subscriber_throws_from_onNext() throws Exception {
        ChatModelListener listener = mock(ChatModelListener.class);
        StreamingChatModel model = createModel(listener, StreamScenario.dribbled(50, 5_000));

        CountDownLatch firstEvent = new CountDownLatch(1);

        model.chat(request()).subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(StreamingEvent event) {
                firstEvent.countDown();
                throw new RuntimeException("boom thrown from subscriber's onNext");
            }

            @Override
            public void onError(Throwable throwable) {}

            @Override
            public void onComplete() {}
        });

        firstEvent.await(10, SECONDS);
        Thread.sleep(3_000); // the stream must be cancelled and no listener callback must fire

        verify(listener).onRequest(any());
        verify(listener, never()).onResponse(any());
        verify(listener, never()).onError(any());
        verifyNoMoreInteractions(listener);
    }

    protected static ChatRequest request() {
        return ChatRequest.builder().messages(UserMessage.from("hi")).build();
    }

    private CompletableFuture<Void> subscribeAndAwaitTermination(StreamingChatModel model) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        model.chat(request()).subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(StreamingEvent event) {}

            @Override
            public void onError(Throwable throwable) {
                done.complete(null);
            }

            @Override
            public void onComplete() {
                done.complete(null);
            }
        });
        return done;
    }
}
