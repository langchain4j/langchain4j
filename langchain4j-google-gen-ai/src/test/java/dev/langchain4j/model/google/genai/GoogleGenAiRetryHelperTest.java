package dev.langchain4j.model.google.genai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class GoogleGenAiRetryHelperTest {

    private static final Logger log = LoggerFactory.getLogger(GoogleGenAiRetryHelperTest.class);

    @Test
    void should_return_result_on_first_attempt() {
        String result = GoogleGenAiRetryHelper.executeWithRetry(() -> "success", 3, log);

        assertThat(result).isEqualTo("success");
    }

    @Test
    void should_retry_and_succeed_on_second_attempt() {
        AtomicInteger attempts = new AtomicInteger(0);

        String result = GoogleGenAiRetryHelper.executeWithRetry(
                () -> {
                    if (attempts.getAndIncrement() == 0) {
                        throw new RuntimeException("First attempt fails");
                    }
                    return "success";
                },
                3,
                log);

        assertThat(result).isEqualTo("success");
        assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    void should_throw_after_all_retries_exhausted() {
        assertThatThrownBy(() -> GoogleGenAiRetryHelper.executeWithRetry(
                        () -> {
                            throw new RuntimeException("Always fails");
                        },
                        0,
                        log))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Google GenAI call failed");
    }

    @Test
    void should_handle_interrupted_exception_during_sleep() {
        AtomicInteger attempts = new AtomicInteger(0);

        Thread.currentThread().interrupt();

        String result = GoogleGenAiRetryHelper.executeWithRetry(
                () -> {
                    if (attempts.getAndIncrement() == 0) {
                        throw new RuntimeException("First attempt fails");
                    }
                    return "success";
                },
                3,
                log);

        assertThat(result).isEqualTo("success");
        // Clear interrupted status
        Thread.interrupted();
    }
}
