package dev.langchain4j.internal;

import dev.langchain4j.model.chat.policy.RetryUtils;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class RetryUtilsTest {
    @Test
    public void test_jitter() {
        assertThat(RetryUtils.DEFAULT_RETRY_POLICY.rawDelayMs(1))
                .isEqualTo(500.0);
        assertThat(RetryUtils.DEFAULT_RETRY_POLICY.rawDelayMs(2))
                .isEqualTo(750.0);
        assertThat(RetryUtils.DEFAULT_RETRY_POLICY.rawDelayMs(3))
                .isEqualTo(1125.0);

        for (int i = 0; i < 100; i++) {
            assertThat(RetryUtils.DEFAULT_RETRY_POLICY.jitterDelayMillis(3))
                    .isBetween(1125, (int) (1125.0 + 1125.0 * 0.2));
        }
    }

    @Test
    void test_withRetry_directly() throws Exception {
        @SuppressWarnings("unchecked")
        Callable<String> mockAction = mock(Callable.class);
        when(mockAction.call()).thenReturn("Success");

        String result = RetryUtils.withRetry(mockAction, 1);

        assertThat(result).isEqualTo("Success");
        verify(mockAction).call();
        verifyNoMoreInteractions(mockAction);
    }

    @Test
    void test_withRetry_noAttempts_directly() throws Exception {
        @SuppressWarnings("unchecked")
        Callable<String> mockAction = mock(Callable.class);
        when(mockAction.call()).thenReturn("Success");

        String result = RetryUtils.withRetry(mockAction);

        assertThat(result).isEqualTo("Success");
        verify(mockAction).call();
        verifyNoMoreInteractions(mockAction);
    }

    @Test
    void testSuccessfulCall() throws Exception {
        @SuppressWarnings("unchecked")
        Callable<String> mockAction = mock(Callable.class);
        when(mockAction.call()).thenReturn("Success");

        String result = RetryUtils.retryPolicyBuilder()
                .delayMillis(100)
                .build()
                .withRetry(mockAction, 3);

        assertThat(result).isEqualTo("Success");
        verify(mockAction).call();
        verifyNoMoreInteractions(mockAction);
    }

    @Test
    void testRetryThenSuccess() throws Exception {
        @SuppressWarnings("unchecked")
        Callable<String> mockAction = mock(Callable.class);
        when(mockAction.call())
                .thenThrow(new RuntimeException())
                .thenReturn("Success");

        long startTime = System.currentTimeMillis();

        String result = RetryUtils.retryPolicyBuilder()
                .delayMillis(100)
                .build()
                .withRetry(mockAction, 3);

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        assertThat(result).isEqualTo("Success");
        verify(mockAction, times(2)).call();
        verifyNoMoreInteractions(mockAction);

        assertThat(duration).isGreaterThanOrEqualTo(100);
    }

    @Test
    void testMaxAttemptsReached() throws Exception {
        @SuppressWarnings("unchecked")
        Callable<String> mockAction = mock(Callable.class);
        when(mockAction.call()).thenThrow(new RuntimeException());

        RetryUtils.RetryPolicy policy = RetryUtils.retryPolicyBuilder()
                .delayMillis(100)
                .build();

        assertThatThrownBy(() -> policy.withRetry(mockAction, 3))
                .isInstanceOf(RuntimeException.class);
        verify(mockAction, times(3)).call();
        verifyNoMoreInteractions(mockAction);
    }

    @Test
    void testZeroAttemptsReached() throws Exception {
        @SuppressWarnings("unchecked")
        Callable<String> mockAction = mock(Callable.class);
        when(mockAction.call()).thenThrow(new RuntimeException());

        RetryUtils.RetryPolicy policy = RetryUtils.retryPolicyBuilder()
                .delayMillis(100)
                .build();

        assertThatThrownBy(() -> policy.withRetry(mockAction, 0))
                .isInstanceOf(RuntimeException.class);
        verify(mockAction, times(1)).call();
        verifyNoMoreInteractions(mockAction);
    }

    @Test
    void testIllegalAttemptsReached() throws Exception {
        @SuppressWarnings("unchecked")
        Callable<String> mockAction = mock(Callable.class);
        when(mockAction.call()).thenThrow(new RuntimeException());

        RetryUtils.RetryPolicy policy = RetryUtils.retryPolicyBuilder()
                .delayMillis(100)
                .build();

        assertThatThrownBy(() -> policy.withRetry(mockAction, -1))
                .isInstanceOf(RuntimeException.class);
        verify(mockAction, times(1)).call();
        verifyNoMoreInteractions(mockAction);
    }
}
