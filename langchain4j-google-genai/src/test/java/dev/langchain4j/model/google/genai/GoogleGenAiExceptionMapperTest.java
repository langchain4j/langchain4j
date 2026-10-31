package dev.langchain4j.model.google.genai;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.genai.errors.GenAiIOException;
import dev.langchain4j.exception.TimeoutException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.Test;

class GoogleGenAiExceptionMapperTest {

    @Test
    void should_map_call_timeout_to_timeout_exception() {
        // this is how OkHttp reports that its call timeout was reached
        InterruptedIOException timeout = new InterruptedIOException("timeout");
        timeout.initCause(new IOException("Canceled"));
        GenAiIOException exception = new GenAiIOException("Failed to execute HTTP request.", timeout);

        RuntimeException mapped = GoogleGenAiExceptionMapper.INSTANCE.mapException(exception);

        assertThat(mapped).isExactlyInstanceOf(TimeoutException.class).hasCause(exception);
    }

    @Test
    void should_map_socket_timeout_to_timeout_exception() {
        GenAiIOException exception =
                new GenAiIOException("Failed to execute HTTP request.", new SocketTimeoutException("Read timed out"));

        RuntimeException mapped = GoogleGenAiExceptionMapper.INSTANCE.mapException(exception);

        assertThat(mapped).isExactlyInstanceOf(TimeoutException.class).hasCause(exception);
    }

    @Test
    void should_not_map_interruption_to_timeout_exception() {
        // OkHttp reports an interrupted thread with the same exception type, but a different message
        GenAiIOException exception =
                new GenAiIOException("Failed to execute HTTP request.", new InterruptedIOException("interrupted"));

        RuntimeException mapped = GoogleGenAiExceptionMapper.INSTANCE.mapException(exception);

        assertThat(mapped).isSameAs(exception);
    }
}
