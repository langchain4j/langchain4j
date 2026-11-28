package dev.langchain4j.internal;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.langchain4j.spi.ExecutorProvider;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

// ExecutorProvider changes the executor process-wide.
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class RetryDispatchTest {

    private static final RetryUtils.RetryPolicy POLICY =
            RetryUtils.retryPolicyBuilder().delayMillis(0).jitterScale(0.0).build();
    private final ExecutorProvider previousProvider = ExecutorProvider.get();

    @AfterEach
    void restoreExecutorProvider() {
        ExecutorProvider.set(previousProvider);
    }

    @Test
    void completes_with_submission_rejection_without_blocking_other_delayed_tasks() throws Exception {
        var rejection = new RejectedExecutionException("executor is full");
        ExecutorProvider.set(() -> command -> {
            throw rejection;
        });
        var initialAttempt = new CompletableFuture<String>();
        var attempts = new AtomicInteger();
        var result = POLICY.withRetryAsync(
                () -> {
                    attempts.incrementAndGet();
                    return initialAttempt;
                },
                1,
                cause -> (RuntimeException) cause);
        var callbackStarted = new CountDownLatch(1);
        var releaseCallback = new CountDownLatch(1);
        var callback = result.whenComplete((value, error) -> {
            callbackStarted.countDown();
            try {
                if (!releaseCallback.await(10, SECONDS)) {
                    throw new AssertionError("completion callback was not released");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        });

        try {
            initialAttempt.completeExceptionally(new IllegalStateException("retry this"));
            assertThat(callbackStarted.await(5, SECONDS))
                    .as("the rejected retry completed the response")
                    .isTrue();
            assertThat(assertThrows(ExecutionException.class, () -> result.get(5, SECONDS))
                            .getCause())
                    .isSameAs(rejection);

            var timerMarker = new CountDownLatch(1);
            CompletableFuture.delayedExecutor(0, MILLISECONDS, Runnable::run).execute(timerMarker::countDown);
            assertThat(timerMarker.await(5, SECONDS))
                    .as("the timer can dispatch another task while a completion callback is blocked")
                    .isTrue();
            assertThat(attempts).hasValue(1);
        } finally {
            releaseCallback.countDown();
            result.cancel(false);
        }
        assertThat(assertThrows(ExecutionException.class, () -> callback.get(5, SECONDS))
                        .getCause())
                .isSameAs(rejection);
    }

    @Test
    void retries_on_the_configured_executor() throws Exception {
        BlockingQueue<Runnable> scheduled = new LinkedBlockingQueue<>();
        ExecutorProvider.set(() -> scheduled::add);
        var attempts = new AtomicInteger();
        var result = POLICY.withRetryAsync(
                () -> attempts.incrementAndGet() == 1
                        ? CompletableFuture.failedFuture(new IllegalStateException("retry this"))
                        : CompletableFuture.completedFuture("ok"),
                1,
                cause -> (RuntimeException) cause);

        Runnable retry = scheduled.poll(5, SECONDS);
        assertThat(retry).isNotNull();
        assertThat(attempts).hasValue(1);

        retry.run();
        assertThat(result.get(5, SECONDS)).isEqualTo("ok");
        assertThat(attempts).hasValue(2);
    }

    @Test
    void keeps_the_mapped_failure_with_no_retries_remaining() {
        BlockingQueue<Runnable> scheduled = new LinkedBlockingQueue<>();
        ExecutorProvider.set(() -> scheduled::add);
        var mapped = new IllegalArgumentException("mapped terminal failure");
        var result = POLICY.withRetryAsync(
                () -> CompletableFuture.failedFuture(new IllegalStateException("initial failure")), 0, cause -> mapped);

        assertThat(assertThrows(ExecutionException.class, () -> result.get(5, SECONDS))
                        .getCause())
                .isSameAs(mapped);
        assertThat(scheduled).isEmpty();
    }

    @Test
    void cancelling_before_a_queued_retry_runs_prevents_another_attempt() throws Exception {
        BlockingQueue<Runnable> scheduled = new LinkedBlockingQueue<>();
        ExecutorProvider.set(() -> scheduled::add);
        var attempts = new AtomicInteger();
        var result = POLICY.withRetryAsync(
                () -> {
                    attempts.incrementAndGet();
                    return CompletableFuture.failedFuture(new IllegalStateException("retry this"));
                },
                1,
                cause -> (RuntimeException) cause);

        Runnable retry = scheduled.poll(5, SECONDS);
        assertThat(retry).isNotNull();
        assertThat(result.cancel(false)).isTrue();

        retry.run();
        assertThat(result).isCancelled();
        assertThat(attempts).hasValue(1);
    }
}
