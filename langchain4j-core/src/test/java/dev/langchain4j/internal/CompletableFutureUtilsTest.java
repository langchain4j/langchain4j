package dev.langchain4j.internal;

import static dev.langchain4j.internal.CompletableFutureUtils.failOnThrow;
import static dev.langchain4j.internal.CompletableFutureUtils.propagateCancellation;
import static dev.langchain4j.internal.CompletableFutureUtils.startSafely;
import static java.util.concurrent.TimeUnit.SECONDS;
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

    @Test
    void failOnThrow_should_return_the_future_of_the_operation() {
        CompletableFuture<String> future = new CompletableFuture<>();

        assertThat(failOnThrow(() -> future)).isSameAs(future);
        assertThat(failOnThrow(this, "operation()", () -> future)).isSameAs(future);
    }

    @Test
    void failOnThrow_should_return_a_failed_future_when_the_operation_throws_an_exception() {
        RuntimeException exception = new IllegalArgumentException("invalid request");

        CompletableFuture<String> future = failOnThrow(() -> {
            throw exception;
        });

        assertThat(future).failsWithin(1, SECONDS).withThrowableThat().havingCause().isSameAs(exception);
    }

    @Test
    void failOnThrow_should_return_a_failed_future_when_the_operation_throws_an_error() {
        NoClassDefFoundError error = new NoClassDefFoundError("MissingOptionalDependency");

        CompletableFuture<String> future = failOnThrow(this, "operation()", () -> {
            throw error;
        });

        assertThat(future).failsWithin(1, SECONDS).withThrowableThat().havingCause().isSameAs(error);
    }

    @Test
    void failOnThrow_should_name_the_method_that_returned_null() {
        CompletableFuture<String> future = failOnThrow(this, "operation()", () -> null);

        assertThat(future)
                .failsWithin(1, SECONDS)
                .withThrowableThat()
                .havingCause()
                .isInstanceOf(IllegalStateException.class)
                .withMessage(CompletableFutureUtilsTest.class.getName() + ".operation() returned null");
    }

    @Test
    void failOnThrow_should_fail_when_an_operation_without_a_name_returns_null() {
        CompletableFuture<String> future = failOnThrow(() -> null);

        assertThat(future)
                .failsWithin(1, SECONDS)
                .withThrowableThat()
                .havingCause()
                .isInstanceOf(IllegalStateException.class)
                .withMessage("The asynchronous operation returned null");
    }

    @Test
    void failOnThrow_should_restore_the_interrupt_flag_when_the_operation_throws_an_interrupted_exception() {

        InterruptedException interrupted = new InterruptedException("interrupted");

        CompletableFuture<String> future = failOnThrow(() -> sneakyThrow(interrupted));
        boolean interruptFlagRestored = Thread.interrupted(); // also clears the flag for the next tests

        assertThat(interruptFlagRestored).isTrue();
        assertThat(future).failsWithin(1, SECONDS).withThrowableThat().havingCause().isSameAs(interrupted);
    }

    @SuppressWarnings("unchecked")
    private static <T, E extends Throwable> T sneakyThrow(Throwable throwable) throws E {
        throw (E) throwable;
    }
}
