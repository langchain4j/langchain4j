package dev.langchain4j.internal;

import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Internal;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Subscribes to {@code upstream} with unbounded demand and re-emits to the subscriber according to <i>its</i>
 * demand, through a bounded queue.
 * <p>
 * This exists to keep a subscriber's {@code request(n)} away from the upstream publisher. mutiny-zero's buffering
 * tube (1.2.1 and 1.3.0) can lose a {@code request(n)} that races an emission from another thread: the item stays
 * in its buffer, is never delivered, and the stream never terminates - a silent hang. Unbounded demand is the one
 * configuration that does not reproduce it, so this operator takes over demand accounting and always requests
 * unbounded upstream.
 * <p>
 * That costs nothing here: the streams this wraps are bounded-rate LLM responses that are consumed eagerly anyway,
 * which is why they were configured with a bounded buffer in the first place. This queue takes over that bound,
 * with the same contract - on overflow the subscriber is terminated with an {@link IllegalStateException} rather
 * than events being dropped or memory growing without limit.
 * <p>
 * <b>Remove this once <a href="https://github.com/smallrye/smallrye-mutiny-zero/issues/365">
 * smallrye-mutiny-zero#365</a> is fixed and the fixed version is adopted.</b> The Reactive Streams TCK
 * verifications in this project are what will show whether it is still needed: drop the wrapping, run them
 * repeatedly, and if they stay green the workaround has served its purpose.
 *
 * @since 1.20.0
 */
@Internal
public final class DemandDecouplingPublisher<T> implements Flow.Publisher<T> {

    private final Flow.Publisher<T> upstream;
    private final int bufferSize;

    public DemandDecouplingPublisher(Flow.Publisher<T> upstream, int bufferSize) {
        this.upstream = ensureNotNull(upstream, "upstream");
        this.bufferSize = ensureGreaterThanZero(bufferSize, "bufferSize");
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        if (subscriber == null) {
            throw new NullPointerException("subscriber");
        }
        upstream.subscribe(new DecouplingSubscriber<>(subscriber, bufferSize));
    }

    private static final class DecouplingSubscriber<T> implements Flow.Subscriber<T>, Flow.Subscription {

        private final Flow.Subscriber<? super T> downstream;
        // lock-free on purpose: a queue that parks on lock contention would block the caller's event-loop thread,
        // which is exactly what the non-blocking modes promise not to do
        private final Queue<T> queue = new ConcurrentLinkedQueue<>();
        private final AtomicInteger queued = new AtomicInteger();
        private final int bufferSize;
        private final AtomicLong requested = new AtomicLong();
        private final AtomicInteger wip = new AtomicInteger();

        private volatile Flow.Subscription upstreamSubscription;
        private volatile boolean done;
        private volatile boolean stopped;
        private Throwable error;

        DecouplingSubscriber(Flow.Subscriber<? super T> downstream, int bufferSize) {
            this.downstream = downstream;
            this.bufferSize = bufferSize;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.upstreamSubscription = subscription;
            // hand the subscriber its subscription first, so that a request() made from within onSubscribe is seen
            downstream.onSubscribe(this);
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(T item) {
            if (done || stopped) {
                return;
            }
            if (queued.incrementAndGet() > bufferSize) {
                queued.decrementAndGet();
                cancelUpstream();
                fail(new IllegalStateException("Buffer overflow: the subscriber is consuming slower than the source "
                        + "produces. Consume faster, or raise the buffer size."));
                return;
            }
            queue.add(item);
            drain();
        }

        @Override
        public void onError(Throwable throwable) {
            if (done || stopped) {
                return;
            }
            fail(throwable);
        }

        @Override
        public void onComplete() {
            if (done || stopped) {
                return;
            }
            done = true;
            drain();
        }

        @Override
        public void request(long n) {
            if (n <= 0) {
                cancelUpstream();
                fail(new IllegalArgumentException("3.9 violated: positive request amount required but was " + n));
                return;
            }
            requested.updateAndGet(current -> {
                long sum = current + n;
                return sum < 0 ? Long.MAX_VALUE : sum; // overflow means unbounded
            });
            drain();
        }

        @Override
        public void cancel() {
            if (stopped) {
                return;
            }
            stopped = true;
            cancelUpstream();
            if (wip.getAndIncrement() == 0) {
                queue.clear();
            }
        }

        private void fail(Throwable throwable) {
            error = throwable;
            done = true;
            drain();
        }

        private void cancelUpstream() {
            Flow.Subscription subscription = upstreamSubscription;
            if (subscription != null) {
                subscription.cancel();
            }
        }

        /** Serializes emission: only one thread drains at a time, so {@code onNext} is never called concurrently. */
        private void drain() {
            if (wip.getAndIncrement() != 0) {
                return;
            }
            int missed = 1;
            while (true) {
                long demand = requested.get();
                long emitted = 0;
                while (emitted != demand) {
                    if (isOver()) {
                        return;
                    }
                    T item = queue.poll();
                    if (item == null) {
                        break;
                    }
                    queued.decrementAndGet();
                    downstream.onNext(item);
                    emitted++;
                }
                if (emitted == demand && isOver()) {
                    return;
                }
                if (emitted != 0 && demand != Long.MAX_VALUE) {
                    requested.addAndGet(-emitted);
                }
                missed = wip.addAndGet(-missed);
                if (missed == 0) {
                    return;
                }
            }
        }

        /**
         * Emits the terminal signal if the stream is over. Called only from the drain loop, so the terminal signal
         * cannot race an {@code onNext}. Returns {@code true} once nothing more may be emitted, which leaves the
         * {@code wip} counter raised on purpose so that later drains are no-ops.
         */
        private boolean isOver() {
            if (stopped) {
                queue.clear();
                return true;
            }
            if (done) {
                Throwable failure = error;
                if (failure != null) {
                    stopped = true;
                    queue.clear();
                    downstream.onError(failure);
                    return true;
                }
                if (queue.isEmpty()) {
                    stopped = true;
                    downstream.onComplete();
                    return true;
                }
            }
            return false;
        }
    }
}
