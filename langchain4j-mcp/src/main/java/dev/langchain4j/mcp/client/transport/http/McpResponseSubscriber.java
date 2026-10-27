package dev.langchain4j.mcp.client.transport.http;

import dev.langchain4j.http.client.sse.HttpErrorResponseReceived;
import dev.langchain4j.http.client.sse.HttpStreamingEvent;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;

/** Serializes response callbacks on the transport executor without buffering an entire response. */
final class McpResponseSubscriber implements Flow.Subscriber<HttpStreamingEvent> {

    private final Flow.Subscriber<? super HttpStreamingEvent> delegate;
    private final Executor executor;
    private final Queue<Runnable> pending = new ArrayDeque<>();
    private Flow.Subscription upstream;
    private long demand;
    private boolean requested;
    private boolean draining;
    private boolean terminal;
    private boolean stopped;

    McpResponseSubscriber(Flow.Subscriber<? super HttpStreamingEvent> delegate, Executor executor) {
        this.delegate = delegate;
        this.executor = executor;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        synchronized (this) {
            if (upstream != null || stopped) {
                subscription.cancel();
                return;
            }
            upstream = subscription;
        }
        // Expose cancellation immediately, even when the executor is busy or the response has not arrived.
        delegate.onSubscribe(new Flow.Subscription() {
            @Override
            public void request(long count) {
                if (count <= 0) {
                    subscription.cancel();
                    onError(new IllegalArgumentException("Demand must be positive"));
                    return;
                }
                synchronized (McpResponseSubscriber.this) {
                    long increased = demand + count;
                    demand = increased < 0 ? Long.MAX_VALUE : increased;
                }
                requestNext();
            }

            @Override
            public void cancel() {
                stop();
            }
        });
    }

    @Override
    public void onNext(HttpStreamingEvent event) {
        if (event instanceof HttpErrorResponseReceived) {
            // Keep the error queued for MCP handling, but stop body reads even if the executor is busy.
            enqueue(() -> delegate.onNext(event), true);
            Flow.Subscription subscription;
            synchronized (this) {
                subscription = upstream;
            }
            if (subscription != null) {
                subscription.cancel();
            }
            return;
        }
        enqueue(
                () -> {
                    delegate.onNext(event);
                    synchronized (this) {
                        requested = false;
                    }
                    requestNext();
                },
                false);
    }

    @Override
    public void onError(Throwable error) {
        enqueue(() -> delegate.onError(error), true);
    }

    @Override
    public void onComplete() {
        enqueue(delegate::onComplete, true);
    }

    private void requestNext() {
        Flow.Subscription subscription;
        synchronized (this) {
            if (stopped || terminal || requested || demand == 0 || upstream == null) {
                return;
            }
            requested = true;
            demand--;
            subscription = upstream;
        }
        subscription.request(1);
    }

    private void enqueue(Runnable callback, boolean last) {
        synchronized (this) {
            if (stopped || terminal) {
                return;
            }
            terminal = last;
            pending.add(callback);
            if (draining) {
                return;
            }
            draining = true;
        }
        try {
            executor.execute(this::drain);
        } catch (RuntimeException error) {
            fail(error);
        }
    }

    private void drain() {
        while (true) {
            Runnable callback;
            synchronized (this) {
                callback = stopped ? null : pending.poll();
                if (callback == null) {
                    draining = false;
                    return;
                }
            }
            try {
                callback.run();
            } catch (RuntimeException error) {
                fail(error);
                return;
            }
        }
    }

    private boolean stop() {
        Flow.Subscription subscription;
        synchronized (this) {
            if (stopped) {
                return false;
            }
            stopped = true;
            pending.clear();
            subscription = upstream;
        }
        if (subscription != null) {
            subscription.cancel();
        }
        return true;
    }

    private void fail(RuntimeException error) {
        if (stop()) {
            // An executor rejection cannot be dispatched onto that executor; still complete the exchange.
            delegate.onError(error);
        }
    }
}
