package dev.langchain4j.internal;

import static dev.langchain4j.internal.CompletableFutureUtils.propagateCancellation;
import static dev.langchain4j.internal.CompletableFutureUtils.startSafely;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

class CompletableFutureUtilsTest {

    @Test
    void start_safely_returns_the_future_of_the_work_itself() {
        var work = new CompletableFuture<String>();

        var started = startSafely(() -> work);
        started.cancel(true);

        assertThat(started).isSameAs(work);
        assertThat(work).isCancelled();
    }

    @Test
    void start_safely_turns_an_exception_thrown_while_starting_into_a_failed_future() {
        var failure = new IllegalStateException("boom");

        CompletableFuture<String> started = startSafely(() -> {
            throw failure;
        });

        assertThat(started)
                .failsWithin(Duration.ofSeconds(1))
                .withThrowableOfType(ExecutionException.class)
                .withCause(failure);
    }

    @Test
    void start_safely_fails_the_future_when_the_work_returns_null_naming_the_future() {
        CompletableFuture<String> unnamed = startSafely(() -> null);
        CompletableFuture<String> named = startSafely(() -> null, "future of the tool");

        assertThat(unnamed)
                .failsWithin(Duration.ofSeconds(1))
                .withThrowableOfType(ExecutionException.class)
                .havingCause()
                .isInstanceOf(IllegalArgumentException.class)
                .withMessage("future cannot be null");
        assertThat(named)
                .failsWithin(Duration.ofSeconds(1))
                .withThrowableOfType(ExecutionException.class)
                .havingCause()
                .isInstanceOf(IllegalArgumentException.class)
                .withMessage("future of the tool cannot be null");
    }

    @Test
    void start_safely_lets_an_error_propagate() {
        var error = new AssertionError("fatal");

        assertThatThrownBy(() -> startSafely(() -> {
                    throw error;
                }))
                .isSameAs(error);
    }

    @Test
    void propagate_cancellation_cancels_the_target_only_when_the_source_is_canceled() {
        var canceledSource = new CompletableFuture<String>();
        var completedSource = new CompletableFuture<String>();
        var firstTarget = new CompletableFuture<String>();
        var secondTarget = new CompletableFuture<String>();

        propagateCancellation(canceledSource, firstTarget);
        propagateCancellation(completedSource, secondTarget);
        propagateCancellation(null, secondTarget);
        canceledSource.cancel(true);
        completedSource.complete("done");

        assertThat(firstTarget).isCancelled();
        assertThat(secondTarget).isNotDone();
    }
}
