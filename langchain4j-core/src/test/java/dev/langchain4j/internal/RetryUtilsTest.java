package dev.langchain4j.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static java.util.concurrent.TimeUnit.SECONDS;

import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.InternalServerException;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RetryUtilsTest {

    @Test
    void jitter() {
        assertThat(RetryUtils.DEFAULT_RETRY_POLICY.rawDelayMs(0)).isEqualTo(500.0);
        assertThat(RetryUtils.DEFAULT_RETRY_POLICY.rawDelayMs(1)).isEqualTo(750.0);
        assertThat(RetryUtils.DEFAULT_RETRY_POLICY.rawDelayMs(2)).isEqualTo(1125.0);

        for (int i = 0; i < 100; i++) {
            assertThat(RetryUtils.DEFAULT_RETRY_POLICY.jitterDelayMillis(2))
                    .isBetween(1125, (int) (1125.0 + 1125.0 * 0.2));
        }
    }

    @Test
    void jitterDelayMillis_returns_base_delay_when_jitter_is_disabled() {
        // jitterScale == 0 disables jitter; the jitter bound is 0, which must not
        // be passed to Random.nextInt (it requires a strictly positive bound).
        RetryUtils.RetryPolicy policy = RetryUtils.retryPolicyBuilder()
                .delayMillis(500)
                .jitterScale(0.0)
                .build();

        assertThat(policy.jitterDelayMillis(0)).isEqualTo(500);
        assertThat(policy.jitterDelayMillis(2)).isEqualTo((int) policy.rawDelayMs(2));
    }

    @Test
    void jitterDelayMillis_returns_base_delay_when_base_delay_too_small_for_jitter() {
        // With a tiny base delay, delay * jitterScale rounds down to a 0 jitter bound.
        RetryUtils.RetryPolicy policy =
                RetryUtils.retryPolicyBuilder().delayMillis(1).jitterScale(0.2).build();

        assertThat(policy.jitterDelayMillis(0)).isEqualTo(1);
    }

    @Test
    void with_retry_directly() throws Exception {
        @SuppressWarnings("unchecked")
        Callable<String> mockAction = mock(Callable.class);
        when(mockAction.call()).thenReturn("Success");

        String result = RetryUtils.withRetry(mockAction, 1);

        assertThat(result).isEqualTo("Success");
        verify(mockAction).call();
        verifyNoMoreInteractions(mockAction);
    }

    @Test
    void with_retry_no_attempts_directly() throws Exception {
        @SuppressWarnings("unchecked")
        Callable<String> mockAction = mock(Callable.class);
        when(mockAction.call()).thenReturn("Success");

        String result = RetryUtils.withRetry(mockAction);

        assertThat(result).isEqualTo("Success");
        verify(mockAction).call();
        verifyNoMoreInteractions(mockAction);
    }

    @Test
    void successfulCall() throws Exception {
        @SuppressWarnings("unchecked")
        Callable<String> mockAction = mock(Callable.class);
        when(mockAction.call()).thenReturn("Success");

        String result = RetryUtils.retryPolicyBuilder().delayMillis(100).build().withRetry(mockAction, 2);

        assertThat(result).isEqualTo("Success");
        verify(mockAction).call();
        verifyNoMoreInteractions(mockAction);
    }

    @Test
    void retryThenSuccess() throws Exception {
        @SuppressWarnings("unchecked")
        Callable<String> mockAction = mock(Callable.class);
        when(mockAction.call()).thenThrow(new RuntimeException()).thenReturn("Success");

        RetryUtils.RetryPolicy retryPolicy =
                RetryUtils.retryPolicyBuilder().delayMillis(100).build();

        long startTime = System.currentTimeMillis();

        String result = retryPolicy.withRetry(mockAction, Integer.MAX_VALUE);

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        assertThat(result).isEqualTo("Success");
        verify(mockAction, times(2)).call();
        verifyNoMoreInteractions(mockAction);

        assertThat(duration).isGreaterThanOrEqualTo(100);
    }

    @Test
    void maxAttemptsReached() throws Exception {
        @SuppressWarnings("unchecked")
        Callable<String> mockAction = mock(Callable.class);
        when(mockAction.call()).thenThrow(new RuntimeException());

        RetryUtils.RetryPolicy policy =
                RetryUtils.retryPolicyBuilder().delayMillis(100).build();

        assertThatThrownBy(() -> policy.withRetry(mockAction, 2)).isInstanceOf(RuntimeException.class);
        verify(mockAction, times(3)).call();
        verifyNoMoreInteractions(mockAction);
    }

    @Test
    void zeroAttemptsReached() throws Exception {
        @SuppressWarnings("unchecked")
        Callable<String> mockAction = mock(Callable.class);
        when(mockAction.call()).thenThrow(new RuntimeException());

        RetryUtils.RetryPolicy policy =
                RetryUtils.retryPolicyBuilder().delayMillis(100).build();

        assertThatThrownBy(() -> policy.withRetry(mockAction, 0)).isInstanceOf(RuntimeException.class);
        verify(mockAction, times(1)).call();
        verifyNoMoreInteractions(mockAction);
    }

    @Test
    void illegalAttemptsReached() throws Exception {
        @SuppressWarnings("unchecked")
        Callable<String> mockAction = mock(Callable.class);
        when(mockAction.call()).thenThrow(new RuntimeException());

        RetryUtils.RetryPolicy policy =
                RetryUtils.retryPolicyBuilder().delayMillis(100).build();

        assertThatThrownBy(() -> policy.withRetry(mockAction, -1)).isInstanceOf(RuntimeException.class);
        verify(mockAction, times(1)).call();
        verifyNoMoreInteractions(mockAction);
    }

    @Test
    void should_not_retry_after_interruption_in_sleep() throws Exception {
        @SuppressWarnings("unchecked")
        Callable<String> mockAction = mock(Callable.class);
        when(mockAction.call()).thenThrow(new RuntimeException("Temporary error"));

        RetryUtils.RetryPolicy policy =
                RetryUtils.retryPolicyBuilder().delayMillis(10000).build();

        Thread testThread = Thread.currentThread();

        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(200);
                testThread.interrupt();
            } catch (InterruptedException ignored) {
            }
        });

        assertThatThrownBy(() -> policy.withRetry(mockAction, 3))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Interrupted while retrying");

        verify(mockAction, times(1)).call();
        assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    void should_not_retry_after_flag_is_set_in_catch() throws Exception {
        @SuppressWarnings("unchecked")
        Callable<String> mockAction = mock(Callable.class);
        when(mockAction.call()).thenAnswer(invocation -> {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Network error");
        });

        RetryUtils.RetryPolicy policy =
                RetryUtils.retryPolicyBuilder().delayMillis(100).build();

        assertThatThrownBy(() -> policy.withRetry(mockAction, 3))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Interrupted during action execution");
        verify(mockAction, times(1)).call();
        verifyNoMoreInteractions(mockAction);
        assertThat(Thread.interrupted()).isTrue();
    }

    private static RetryUtils.RetryPolicy fastPolicy() {
        return RetryUtils.retryPolicyBuilder().delayMillis(1).jitterScale(0.0).build();
    }

    @Test
    void withRetryAsync_succeeds_on_the_first_attempt() throws Exception {
        AtomicInteger attempts = new AtomicInteger();

        CompletableFuture<String> result = fastPolicy()
                .withRetryAsync(
                        () -> {
                            attempts.incrementAndGet();
                            return CompletableFuture.completedFuture("ok");
                        },
                        2,
                        ExceptionMapper.DEFAULT);

        assertThat(result.get(5, SECONDS)).isEqualTo("ok");
        assertThat(attempts).hasValue(1);
    }

    @Test
    void withRetryAsync_retries_a_retriable_failure_then_succeeds() throws Exception {
        AtomicInteger attempts = new AtomicInteger();

        CompletableFuture<String> result = fastPolicy()
                .withRetryAsync(
                        () -> {
                            if (attempts.incrementAndGet() < 3) {
                                return CompletableFuture.failedFuture(new HttpException(429, "rate limited"));
                            }
                            return CompletableFuture.completedFuture("ok");
                        },
                        2,
                        ExceptionMapper.DEFAULT);

        assertThat(result.get(5, SECONDS)).isEqualTo("ok");
        assertThat(attempts).as("two failures + one success").hasValue(3);
    }

    @Test
    void withRetryAsync_does_not_retry_a_non_retriable_failure() {
        AtomicInteger attempts = new AtomicInteger();

        CompletableFuture<String> result = fastPolicy()
                .withRetryAsync(
                        () -> {
                            attempts.incrementAndGet();
                            return CompletableFuture.failedFuture(new HttpException(401, "unauthorized"));
                        },
                        5,
                        ExceptionMapper.DEFAULT);

        // a 401 maps to AuthenticationException (a NonRetriableException) - it is not retried
        assertThatThrownBy(() -> result.get(5, SECONDS)).hasCauseInstanceOf(AuthenticationException.class);
        assertThat(attempts).hasValue(1);
    }

    @Test
    void withRetryAsync_exhausts_retries_and_fails_with_the_mapped_exception() {
        AtomicInteger attempts = new AtomicInteger();

        CompletableFuture<String> result = fastPolicy()
                .withRetryAsync(
                        () -> {
                            attempts.incrementAndGet();
                            return CompletableFuture.failedFuture(new HttpException(503, "unavailable"));
                        },
                        2,
                        ExceptionMapper.DEFAULT);

        // a 503 maps to InternalServerException (retriable); after maxRetries the mapped exception surfaces
        assertThatThrownBy(() -> result.get(5, SECONDS)).hasCauseInstanceOf(InternalServerException.class);
        assertThat(attempts).as("maxRetries (2) + 1").hasValue(3);
    }

    @Test
    void withRetryAsync_cancellation_stops_further_retries() throws Exception {
        AtomicInteger attempts = new AtomicInteger();

        CompletableFuture<String> result = RetryUtils.retryPolicyBuilder()
                .delayMillis(300)
                .jitterScale(0.0)
                .build()
                .withRetryAsync(
                        () -> {
                            attempts.incrementAndGet();
                            return CompletableFuture.failedFuture(new HttpException(503, "unavailable"));
                        },
                        10,
                        ExceptionMapper.DEFAULT);

        // the first attempt fails immediately, then a ~300ms backoff is scheduled; cancel during that window
        Thread.sleep(50);
        assertThat(result.cancel(true)).isTrue();
        Thread.sleep(500); // past when the next attempt would have run

        assertThat(result).isCancelled();
        assertThat(attempts).as("no further attempts after cancellation").hasValue(1);
    }
}
