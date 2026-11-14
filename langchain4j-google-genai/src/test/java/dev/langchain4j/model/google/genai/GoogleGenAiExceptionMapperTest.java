package dev.langchain4j.model.google.genai;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.TimeoutException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

class GoogleGenAiExceptionMapperTest {

    private final GoogleGenAiExceptionMapper mapper = GoogleGenAiExceptionMapper.INSTANCE;

    @Test
    void should_map_api_exceptions_by_status_code() {
        assertThat(mapper.mapException(new ApiException(400, "INVALID_ARGUMENT", "bad")))
                .isInstanceOf(InvalidRequestException.class);
        assertThat(mapper.mapException(new ApiException(403, "PERMISSION_DENIED", "denied")))
                .isInstanceOf(AuthenticationException.class);
        assertThat(mapper.mapException(new ApiException(429, "RESOURCE_EXHAUSTED", "slow down")))
                .isInstanceOf(RateLimitException.class);
        assertThat(mapper.mapException(new CompletionException(new ApiException(500, "INTERNAL", "oops"))))
                .isInstanceOf(InternalServerException.class);
    }

    @Test
    void should_map_timeouts_to_timeout_exception() {
        assertThat(mapper.mapException(new GenAiIOException("Failed", new SocketTimeoutException("read timed out"))))
                .isInstanceOf(TimeoutException.class);
        // OkHttp's call timeout, set with HttpOptions.timeout(...)
        assertThat(mapper.mapException(new GenAiIOException("Failed", new InterruptedIOException("timeout"))))
                .isInstanceOf(TimeoutException.class);
        assertThat(mapper.mapException(new CompletionException(new InterruptedIOException("timeout"))))
                .isInstanceOf(TimeoutException.class);
    }

    @Test
    void should_not_take_an_interrupted_call_for_a_timeout() {
        RuntimeException mapped =
                mapper.mapException(new GenAiIOException("Failed", new InterruptedIOException("interrupted")));

        assertThat(mapped).isNotInstanceOf(TimeoutException.class).isInstanceOf(GenAiIOException.class);
    }

    @Test
    void should_unwrap_completion_exception() {
        IllegalStateException cause = new IllegalStateException("boom");

        assertThat(mapper.mapException(new CompletionException(cause))).isSameAs(cause);
        assertThat(mapper.mapException(new CompletionException(new IOException("io"))))
                .isExactlyInstanceOf(LangChain4jException.class);
    }
}
