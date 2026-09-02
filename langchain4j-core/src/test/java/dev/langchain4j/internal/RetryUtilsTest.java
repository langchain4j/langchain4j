package dev.langchain4j.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
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
    void exponentialBackoff_delay_increases_across_attempts() {
        RetryUtils.RetryPolicy policy =
                RetryUtils.retryPolicyBuilder().delayMillis(100).backoffExp(2.0).build();

        assertThat(policy.rawDelayMs(0)).isEqualTo(100.0);
        assertThat(policy.rawDelayMs(1)).isEqualTo(200.0);
        assertThat(policy.rawDelayMs(2)).isEqualTo(400.0);
        assertThat(policy.rawDelayMs(3)).isEqualTo(800.0);
    }

    @Test
    void maxIntervalMillis_caps_the_raw_delay() {
        RetryUtils.RetryPolicy policy = RetryUtils.retryPolicyBuilder()
                .delayMillis(100)
                .backoffExp(2.0)
                .maxIntervalMillis(300)
                .build();

        assertThat(policy.rawDelayMs(0)).isEqualTo(100.0);
        assertThat(policy.rawDelayMs(1)).isEqualTo(200.0);
        // uncapped would be 400, capped at 300
        assertThat(policy.rawDelayMs(2)).isEqualTo(300.0);
        // uncapped would be 800, still capped at 300
        assertThat(policy.rawDelayMs(5)).isEqualTo(300.0);
    }

    @Test
    void jitter_is_applied_within_expected_range() {
        RetryUtils.RetryPolicy policy = RetryUtils.retryPolicyBuilder()
                .delayMillis(100)
                .backoffExp(2.0)
                .jitterScale(0.5)
                .build();

        double rawDelay = policy.rawDelayMs(1); // 200.0
        for (int i = 0; i < 100; i++) {
            assertThat(policy.jitterDelayMillis(1)).isBetween((int) rawDelay, (int) (rawDelay + rawDelay * 0.5));
        }
    }

    @Test
    void withExponentialBackoff_retries_stop_after_maxAttempts() throws Exception {
        @SuppressWarnings("unchecked")
        Callable<String> mockAction = mock(Callable.class);
        when(mockAction.call()).thenThrow(new RuntimeException("always fails"));

        assertThatThrownBy(() -> RetryUtils.withExponentialBackoff(mockAction, 10L, 2.0, 100L, 3))
                .isInstanceOf(RuntimeException.class);

        verify(mockAction, times(3)).call();
        verifyNoMoreInteractions(mockAction);
    }

    @Test
    void withExponentialBackoff_succeeds_on_nth_attempt() throws Exception {
        @SuppressWarnings("unchecked")
        Callable<String> mockAction = mock(Callable.class);
        when(mockAction.call())
                .thenThrow(new RuntimeException("fail 1"))
                .thenThrow(new RuntimeException("fail 2"))
                .thenReturn("Success");

        String result = RetryUtils.withExponentialBackoff(mockAction, 10L, 2.0, 100L, 5);

        assertThat(result).isEqualTo("Success");
        verify(mockAction, times(3)).call();
        verifyNoMoreInteractions(mockAction);
    }

    @Test
    void withExponentialBackoff_rejects_non_positive_maxAttempts() {
        @SuppressWarnings("unchecked")
        Callable<String> mockAction = mock(Callable.class);

        assertThatThrownBy(() -> RetryUtils.withExponentialBackoff(mockAction, 10L, 2.0, 100L, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withRetry_using_custom_policy_overload() throws Exception {
        @SuppressWarnings("unchecked")
        Callable<String> mockAction = mock(Callable.class);
        when(mockAction.call()).thenReturn("Success");

        RetryUtils.RetryPolicy policy =
                RetryUtils.retryPolicyBuilder().delayMillis(10).maxRetries(1).build();

        String result = RetryUtils.withRetry(mockAction, policy);

        assertThat(result).isEqualTo("Success");
        verify(mockAction).call();
        verifyNoMoreInteractions(mockAction);
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
}
