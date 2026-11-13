package dev.langchain4j.internal;

import static dev.langchain4j.internal.CompletableFutureUtils.failOnThrow;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class CompletableFutureUtilsTest {

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
