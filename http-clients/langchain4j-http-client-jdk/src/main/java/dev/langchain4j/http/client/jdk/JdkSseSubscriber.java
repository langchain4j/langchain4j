package dev.langchain4j.http.client.jdk;

import static dev.langchain4j.http.client.sse.ServerSentEventListenerUtils.ignoringExceptions;

import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventContext;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import java.util.concurrent.Flow;

/**
 * A {@link Flow.Subscriber} that consumes SSE lines delivered by
 * {@link java.net.http.HttpResponse.BodySubscribers#fromLineSubscriber(Flow.Subscriber)},
 * accumulates them into {@link ServerSentEvent}s and forwards them to a
 * {@link ServerSentEventListener}.
 *
 * <p>Compared to the {@code BodyHandlers.ofInputStream()} based path, this subscriber
 * does not hold a thread of the underlying {@link java.net.http.HttpClient}'s executor
 * for the duration of the stream — events are pushed to the listener on the I/O
 * dispatcher threads, with the usual {@link Flow.Subscriber} ordering guarantees.
 *
 * <p>Backpressure is handled by requesting one line at a time after each delivered
 * event, mirroring the pattern used by the MCP transport.
 */
class JdkSseSubscriber implements Flow.Subscriber<String> {

    private final ServerSentEventListener listener;
    private final SubscriptionParsingHandle handle;
    private final ServerSentEventContext context;

    private String event;
    private final StringBuilder data = new StringBuilder();

    JdkSseSubscriber(ServerSentEventListener listener) {
        this.listener = listener;
        this.handle = new SubscriptionParsingHandle();
        this.context = new ServerSentEventContext(handle);
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        handle.onSubscribe(subscription);
        if (!handle.isCancelled()) {
            subscription.request(1);
        }
    }

    @Override
    public void onNext(String line) {
        if (handle.isCancelled()) {
            return;
        }

        if (line.isEmpty()) {
            flushPendingEvent();
        } else if (line.startsWith("event:")) {
            event = line.substring("event:".length()).trim();
        } else if (line.startsWith("data:")) {
            String content = line.substring("data:".length());
            if (!data.isEmpty()) {
                data.append("\n");
            }
            data.append(content.trim());
        }

        if (!handle.isCancelled()) {
            handle.requestNext();
        }
    }

    @Override
    public void onError(Throwable throwable) {
        ignoringExceptions(() -> listener.onError(throwable));
    }

    @Override
    public void onComplete() {
        if (!handle.isCancelled()) {
            flushPendingEvent();
        }
        ignoringExceptions(listener::onClose);
    }

    private void flushPendingEvent() {
        if (data.isEmpty()) {
            return;
        }
        ServerSentEvent sse = new ServerSentEvent(event, data.toString());
        ignoringExceptions(() -> listener.onEvent(sse, context));
        event = null;
        data.setLength(0);
    }
}
