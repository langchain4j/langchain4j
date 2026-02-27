package dev.langchain4j.experimental.durable.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {

    @Test
    void should_build_with_defaults() {
        RetryPolicy policy = RetryPolicy.builder().build();

        assertThat(policy.maxRetries()).isEqualTo(3);
        assertThat(policy.initialDelay()).isEqualTo(Duration.ofSeconds(1));
        assertThat(policy.backoffMultiplier()).isEqualTo(2.0);
        assertThat(policy.maxDelay()).isEqualTo(Duration.ofMinutes(1));
        assertThat(policy.retryableExceptions()).isEmpty();
    }

    @Test
    void should_build_with_custom_values() {
        RetryPolicy policy = RetryPolicy.builder()
                .maxRetries(5)
                .initialDelay(Duration.ofMillis(500))
                .backoffMultiplier(1.5)
                .maxDelay(Duration.ofSeconds(30))
                .retryableException(IOException.class)
                .retryableException(IllegalStateException.class)
                .build();

        assertThat(policy.maxRetries()).isEqualTo(5);
        assertThat(policy.initialDelay()).isEqualTo(Duration.ofMillis(500));
        assertThat(policy.backoffMultiplier()).isEqualTo(1.5);
        assertThat(policy.maxDelay()).isEqualTo(Duration.ofSeconds(30));
        assertThat(policy.retryableExceptions())
                .containsExactlyInAnyOrder(IOException.class, IllegalStateException.class);
    }

    @Test
    void should_compute_exponential_delay() {
        RetryPolicy policy = RetryPolicy.builder()
                .initialDelay(Duration.ofSeconds(1))
                .backoffMultiplier(2.0)
                .maxDelay(Duration.ofMinutes(1))
                .build();

        assertThat(policy.delayForAttempt(1)).isEqualTo(Duration.ofSeconds(1));
        assertThat(policy.delayForAttempt(2)).isEqualTo(Duration.ofSeconds(2));
        assertThat(policy.delayForAttempt(3)).isEqualTo(Duration.ofSeconds(4));
        assertThat(policy.delayForAttempt(4)).isEqualTo(Duration.ofSeconds(8));
    }

    @Test
    void should_cap_delay_at_max() {
        RetryPolicy policy = RetryPolicy.builder()
                .initialDelay(Duration.ofSeconds(10))
                .backoffMultiplier(3.0)
                .maxDelay(Duration.ofSeconds(30))
                .build();

        // attempt 1: 10s, attempt 2: 30s (capped), attempt 3: 30s (capped)
        assertThat(policy.delayForAttempt(1)).isEqualTo(Duration.ofSeconds(10));
        assertThat(policy.delayForAttempt(2)).isEqualTo(Duration.ofSeconds(30));
        assertThat(policy.delayForAttempt(3)).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void should_be_retryable_when_no_exceptions_configured() {
        RetryPolicy policy = RetryPolicy.builder().build();

        assertThat(policy.isRetryable(new RuntimeException("boom"))).isTrue();
        assertThat(policy.isRetryable(new IOException("io"))).isTrue();
    }

    @Test
    void should_filter_by_retryable_exceptions() {
        RetryPolicy policy =
                RetryPolicy.builder().retryableException(IOException.class).build();

        assertThat(policy.isRetryable(new IOException("io"))).isTrue();
        assertThat(policy.isRetryable(new RuntimeException("runtime"))).isFalse();
    }

    @Test
    void should_match_cause_chain() {
        RetryPolicy policy =
                RetryPolicy.builder().retryableException(IOException.class).build();

        Exception wrappedException = new RuntimeException("wrapped", new IOException("cause"));
        assertThat(policy.isRetryable(wrappedException)).isTrue();
    }

    @Test
    void should_never_retry_task_paused_exception() {
        RetryPolicy policy = RetryPolicy.builder().build();

        assertThat(policy.isRetryable(new TaskPausedException("paused", "key"))).isFalse();
    }

    @Test
    void none_should_never_retry() {
        RetryPolicy none = RetryPolicy.NONE;

        assertThat(none.maxRetries()).isZero();
        assertThat(none.isRetryable(new RuntimeException("boom"))).isTrue();
    }

    @Test
    void should_reject_negative_max_retries() {
        assertThatThrownBy(() -> RetryPolicy.builder().maxRetries(-1).build())
                .isInstanceOf(IllegalArgumentException.class);
    }
}
