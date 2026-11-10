package dev.langchain4j.agentic.internal;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Internal;
import dev.langchain4j.internal.DefaultExecutorProvider;
import dev.langchain4j.spi.CapturedContext;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A value resolved lazily, the first time it is needed, from something that may be unavailable or slow, like the
 * server of a remote agent.
 * <p>
 * The value is resolved by one attempt at a time, running on a thread of the LangChain4j executor, and every caller
 * needing the value meanwhile waits on that attempt instead of making its own. When the attempt fails, the callers
 * waiting on it all fail with its failure, and the failure is not cached: the next call starts a new attempt.
 * <p>
 * An attempt can be given a timeout, counted from its start. At that deadline the callers waiting on it fail with a
 * {@link TimeoutException} and the thread resolving the value is interrupted. The attempt stays the current one
 * until that thread really ends: a call arriving in the meantime fails at once with a {@link StillRunningException},
 * so that the value is never resolved twice at the same time, and a value resolved after the deadline is kept for
 * the next call. A warning is logged at the deadline, and when a value resolved after it is kept.
 *
 * @param <T> the type of the resolved value
 */
@Internal
public final class LazyResolution<T> {

    private static final Logger LOG = LoggerFactory.getLogger(LazyResolution.class);

    /**
     * Thrown to a caller arriving after the deadline of an attempt whose thread has not ended yet: nothing was
     * tried for this caller, since the value cannot be resolved again until that thread ends.
     */
    public static class StillRunningException extends TimeoutException {

        private StillRunningException(String message) {
            super(message);
        }
    }

    // Held only to read or change the fields below, never while the value is being resolved
    private final ReentrantLock lock = new ReentrantLock();

    private final Supplier<String> description;
    private Supplier<T> resolver;
    private Attempt attempt;
    private volatile T value;

    /**
     * @param description says what is resolved in the warnings logged for an attempt that outlives its timeout, like
     *                    "the agent card of A2A agent 'x'": it is asked only when such a warning is logged
     * @param resolver resolves the value, on a thread of the LangChain4j executor: it returns a non-null value or
     *                 throws an unchecked exception, and it is released once the value is resolved
     */
    public LazyResolution(Supplier<String> description, Supplier<T> resolver) {
        this.description = ensureNotNull(description, "description");
        this.resolver = ensureNotNull(resolver, "resolver");
    }

    private LazyResolution(T value) {
        this.description = null;
        this.value = value;
    }

    /**
     * @return a resolution whose value is already known
     */
    public static <T> LazyResolution<T> resolved(T value) {
        return new LazyResolution<>(ensureNotNull(value, "value"));
    }

    /**
     * @return the value if it has already been resolved, {@code null} otherwise: it never resolves it
     */
    public T peek() {
        return value;
    }

    /**
     * Returns the value, resolving it first if needed, or waiting for the attempt that is already resolving it.
     *
     * @param timeout the time given to the attempt started by this call, or {@code null} for no limit: a call
     *                joining an attempt already started waits until the deadline of that attempt
     * @return the resolved value
     * @throws RuntimeException the failure of the attempt, the same instance for every caller waiting on it
     * @throws TimeoutException when the attempt did not resolve the value within its timeout, and a
     *                          {@link StillRunningException} when it did not before this call, and its thread has
     *                          not ended yet
     * @throws InterruptedException when the calling thread is interrupted while waiting, which does not stop the
     *                              attempt
     */
    public T get(Duration timeout) throws InterruptedException, TimeoutException {
        T resolved = value;
        if (resolved != null) {
            return resolved;
        }
        Attempt current;
        boolean created = false;
        lock.lock();
        try {
            resolved = value;
            if (resolved != null) {
                return resolved;
            }
            current = attempt;
            if (current == null) {
                current = new Attempt(resolver, timeout);
                attempt = current;
                created = true;
            }
        } finally {
            lock.unlock();
        }
        if (created) {
            current.start();
        }
        return current.await(!created);
    }

    /**
     * Records the end of an attempt: this happens before the callers waiting on it are released, so that the call
     * that one of them makes next finds the resolved value, or starts a new attempt.
     */
    private void end(Attempt ended, T resolved) {
        lock.lock();
        try {
            if (resolved != null) {
                value = resolved;
                resolver = null;
            }
            if (attempt == ended) {
                attempt = null;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * How a timeout is shown in the messages of the failures reporting it, like {@code 30 s} or {@code 200 ms}.
     */
    public static String describe(Duration timeout) {
        return timeout.toMillis() % 1000 == 0 ? timeout.toSeconds() + " s" : timeout.toMillis() + " ms";
    }

    private class Attempt {

        private final Supplier<T> resolver;
        private final Duration timeout;
        private final long startNanos = System.nanoTime();

        // What the callers wait on: completed by the worker, by the deadline, or when the worker cannot be started
        private final CompletableFuture<T> outcome = new CompletableFuture<>();

        // A FutureTask, because its cancellation interrupts the thread running it only while it is running it
        private final FutureTask<Void> worker = new FutureTask<>(this::resolve, null);

        // Set by the worker when it starts resolving, or when the attempt is given up before any worker started
        private final AtomicBoolean claimed = new AtomicBoolean();

        Attempt(Supplier<T> resolver, Duration timeout) {
            this.resolver = resolver;
            this.timeout = timeout;
        }

        void start() {
            try {
                if (timeout != null) {
                    // completes the outcome on the JDK thread scheduling delays, also when nobody waits on it any more
                    outcome.orTimeout(TimeUnit.NANOSECONDS.convert(timeout), TimeUnit.NANOSECONDS);
                }
                outcome.whenComplete((resolved, failure) -> {
                    if (failure instanceof TimeoutException) {
                        expire();
                    }
                });
                CapturedContext context = DefaultExecutorProvider.captureContext();
                DefaultExecutorProvider.getDefaultExecutor().execute(context != null ? context.wrap(worker) : worker);
            } catch (Throwable notStarted) {
                // Whatever prevents the worker from starting, an Error included, like a provider of the executor that
                // cannot be loaded or a thread that cannot be created: an attempt left in place would block every
                // later caller, forever when there is no timeout
                if (claimed.compareAndSet(false, true)) {
                    end(this, null);
                    outcome.completeExceptionally(notStarted);
                }
            }
        }

        /**
         * At the deadline the worker is interrupted, and it ends the attempt when it returns. An attempt whose worker
         * never started is ended here, since nothing else would.
         */
        private void expire() {
            worker.cancel(true);
            if (claimed.compareAndSet(false, true)) {
                end(this, null);
                LOG.warn(
                        "Timeout of {} elapsed before the executor started resolving {}",
                        describe(timeout),
                        description.get());
            } else {
                LOG.warn(
                        "Timeout of {} elapsed while resolving {}: the thread resolving it has been interrupted, and"
                                + " the calls arriving before it ends fail at once",
                        describe(timeout),
                        description.get());
            }
        }

        private void resolve() {
            if (!claimed.compareAndSet(false, true)) {
                return;
            }
            T resolved = null;
            Throwable failure = null;
            try {
                resolved = resolver.get();
            } catch (Throwable e) {
                failure = e;
            }
            // a value resolved after the deadline is kept, while completing the outcome is then a no-op
            end(this, resolved);
            if (failure != null) {
                outcome.completeExceptionally(failure);
            } else if (!outcome.complete(resolved)) {
                LOG.warn(
                        "Resolved {} after {}, later than the timeout of {}: the value is kept",
                        description.get(),
                        describe(Duration.ofNanos(System.nanoTime() - startNanos)),
                        describe(timeout));
            }
        }

        T await(boolean joined) throws InterruptedException, TimeoutException {
            // a caller joining the attempt after its deadline did not wait for the timeout: nothing was tried for it
            boolean late = joined && outcome.isDone();
            try {
                return outcome.get();
            } catch (ExecutionException e) {
                Throwable failure = e.getCause();
                if (failure instanceof TimeoutException) {
                    if (late) {
                        throw new StillRunningException(
                                "The previous resolution is still running after its timeout of " + describe(timeout));
                    }
                    throw new TimeoutException("Not resolved within " + describe(timeout));
                }
                if (failure instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                if (failure instanceof Error error) {
                    throw error;
                }
                throw new IllegalStateException(failure);
            }
        }
    }
}
