package dev.langchain4j.model.openai.internal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.exception.AsyncNotSupportedException;
import java.time.Duration;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class SyncOrAsyncTest {

    @Test
    void executeRawAsync_should_return_a_failed_future_by_default() {

        SyncOrAsync<String> syncOrAsync = new SyncOrAsync<>() {

            @Override
            public String execute() {
                return "response";
            }

            @Override
            public AsyncResponseHandling onResponse(Consumer<String> responseHandler) {
                throw new UnsupportedOperationException();
            }
        };

        assertThat(syncOrAsync.executeRawAsync())
                .failsWithin(Duration.ofSeconds(1))
                .withThrowableThat()
                .havingCause()
                .isInstanceOf(AsyncNotSupportedException.class);
    }
}
