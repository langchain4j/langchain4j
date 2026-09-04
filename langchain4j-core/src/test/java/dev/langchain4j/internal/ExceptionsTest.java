package dev.langchain4j.internal;

import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

class ExceptionsTest implements WithAssertions {
    @Test
    void illegal_argument() {
        assertThat(Exceptions.illegalArgument("test %s", "test"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("test test");
    }

    @Test
    void runtime() {
        assertThat(Exceptions.runtime("test %s", "test"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("test test");
    }

    @Test
    void unwrapCompletionException_returns_non_wrapper_throwable_unchanged() {
        RuntimeException cause = new RuntimeException("boom");
        assertThat(Exceptions.unwrapCompletionException(cause)).isSameAs(cause);
    }

    @Test
    void unwrapCompletionException_unwraps_CompletionException() {
        RuntimeException cause = new RuntimeException("boom");
        assertThat(Exceptions.unwrapCompletionException(new CompletionException(cause))).isSameAs(cause);
    }

    @Test
    void unwrapCompletionException_unwraps_ExecutionException() {
        RuntimeException cause = new RuntimeException("boom");
        assertThat(Exceptions.unwrapCompletionException(new ExecutionException(cause))).isSameAs(cause);
    }

    @Test
    void unwrapCompletionException_unwraps_nested_wrappers() {
        // e.g. a blocking Future.get() (ExecutionException) bridged into CompletableFuture composition
        // (CompletionException) - the real cause must still be reachable
        RuntimeException cause = new RuntimeException("boom");
        Throwable nested = new CompletionException(new ExecutionException(cause));
        assertThat(Exceptions.unwrapCompletionException(nested)).isSameAs(cause);
    }

    @Test
    void unwrapCompletionException_returns_wrapper_unchanged_when_cause_is_null() {
        CompletionException noCause = new CompletionException(null);
        assertThat(Exceptions.unwrapCompletionException(noCause)).isSameAs(noCause);
    }

    @Test
    void unwrapCompletionException_handles_null() {
        assertThat(Exceptions.unwrapCompletionException(null)).isNull();
    }
}
