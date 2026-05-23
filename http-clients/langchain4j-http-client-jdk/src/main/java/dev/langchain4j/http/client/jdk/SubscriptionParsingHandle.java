package dev.langchain4j.http.client.jdk;

import dev.langchain4j.http.client.sse.ServerSentEventParsingHandle;
import java.util.concurrent.Flow;

/**
 * {@link ServerSentEventParsingHandle} implementation that cancels the underlying
 * reactive subscription. Used by the non-blocking SSE path in {@link JdkHttpClient}
 * to expose cancellation to {@link dev.langchain4j.http.client.sse.ServerSentEventListener}.
 */
class SubscriptionParsingHandle implements ServerSentEventParsingHandle {

    private volatile Flow.Subscription subscription;
    private volatile boolean cancelled;

    void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        if (cancelled) {
            subscription.cancel();
        }
    }

    void requestNext() {
        Flow.Subscription current = subscription;
        if (current != null) {
            current.request(1);
        }
    }

    @Override
    public void cancel() {
        cancelled = true;
        Flow.Subscription current = subscription;
        if (current != null) {
            current.cancel();
        }
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }
}
