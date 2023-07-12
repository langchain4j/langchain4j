package dev.langchain4j.internal;

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
    }

    @Test
    void testRetryThenSuccess() throws Exception {
        Callable<String> mockAction = mock(Callable.class);
        when(mockAction.call()).thenThrow(new RuntimeException()).thenReturn("Success");

        String result = withRetry(mockAction, 3);

        assertThat(result).isEqualTo("Success");
        verify(mockAction, times(2)).call();
    }

    @Test
    void testMaxAttemptsReached() throws Exception {
        Callable<String> mockAction = mock(Callable.class);
        when(mockAction.call()).thenThrow(new RuntimeException());

        assertThatThrownBy(() -> withRetry(mockAction, 3))
                .isInstanceOf(RuntimeException.class);
        verify(mockAction, times(3)).call();
    }
}