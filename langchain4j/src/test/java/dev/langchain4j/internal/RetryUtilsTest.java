package dev.langchain4j.internal;

import dev.ai4j.openai4j.OpenAiHttpException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class RetryUtilsTest {

    @Test
    void testSuccessfulCall() throws Exception {
        Callable<String> mockAction = mock(Callable.class);
        when(mockAction.call()).thenReturn("Success");

        String result = withRetry(mockAction, 3);

        assertThat(result).isEqualTo("Success");
        verify(mockAction).call();
        verifyNoMoreInteractions(mockAction);
    }

    @Test
    void testRetryThenSuccess() throws Exception {
        Callable<String> mockAction = mock(Callable.class);
        when(mockAction.call())
                .thenThrow(new RuntimeException())
                .thenReturn("Success");

        String result = withRetry(mockAction, 3);

        assertThat(result).isEqualTo("Success");
        verify(mockAction, times(2)).call();
        verifyNoMoreInteractions(mockAction);
    }

    @Test
    void testMaxAttemptsReached() throws Exception {
        Callable<String> mockAction = mock(Callable.class);
        when(mockAction.call()).thenThrow(new RuntimeException());

        assertThatThrownBy(() -> withRetry(mockAction, 3))
                .isInstanceOf(RuntimeException.class);
        verify(mockAction, times(3)).call();
        verifyNoMoreInteractions(mockAction);
    }

    @Test
    void should_not_retry_401_unauthorized() throws Exception {
        Callable<String> mockAction = mock(Callable.class);
        when(mockAction.call()).thenThrow(new OpenAiHttpException(401, "Unauthorized"));

        assertThatThrownBy(() -> withRetry(mockAction, 3))
                .isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(OpenAiHttpException.class)
                .hasRootCauseMessage("Unauthorized");

        verify(mockAction).call();
        verifyNoMoreInteractions(mockAction);
    }

    @Test
    void should_wait_1_second_before_retry_when_429_too_many_requests() throws Exception {
        Callable<String> mockAction = mock(Callable.class);
        when(mockAction.call())
                .thenThrow(new OpenAiHttpException(429, "Too many requests"))
                .thenReturn("Success");

        long startTime = System.currentTimeMillis();

        String result = withRetry(mockAction, 3);

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        assertThat(result).isEqualTo("Success");
        verify(mockAction, times(2)).call();
        verifyNoMoreInteractions(mockAction);

        assertThat(duration).isGreaterThanOrEqualTo(1000);
    }
}