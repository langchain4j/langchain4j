package dev.langchain4j.invocation;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CurrentInvocationTest {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @AfterEach
    void cleanUp() {
        executor.shutdownNow();
    }

    @Test
    void should_restore_the_parent_after_a_nested_scope_fails() {
        InvocationContext parent = context();
        InvocationContext child = context();

        assertThat(CurrentInvocation.invocationId()).isNull();
        try (var ignored = CurrentInvocation.enter(parent)) {
            assertThatThrownBy(() -> {
                        try (var nested = CurrentInvocation.enter(child)) {
                            assertThat(CurrentInvocation.invocationId()).isEqualTo(child.invocationId());
                            throw new IllegalStateException("child failure");
                        }
                    })
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("child failure");
            assertThat(CurrentInvocation.invocationId()).isEqualTo(parent.invocationId());
        }
        assertThat(CurrentInvocation.invocationId()).isNull();
    }

    @Test
    void should_restore_a_reused_executor_thread_even_after_failure() throws Exception {
        InvocationContext invocation = context();
        CompletableFuture<Void> failed = CompletableFuture.runAsync(
                () -> {
                    try (var ignored = CurrentInvocation.enter(invocation)) {
                        assertThat(CurrentInvocation.invocationId()).isEqualTo(invocation.invocationId());
                        throw new IllegalStateException("tool failure");
                    }
                },
                executor);

        assertThatThrownBy(() -> failed.get(10, SECONDS)).hasCauseInstanceOf(IllegalStateException.class);
        assertThat(executor.submit(CurrentInvocation::invocationId).get(10, SECONDS))
                .isNull();
        assertThat(CurrentInvocation.invocationId()).isNull();
    }

    private static InvocationContext context() {
        return InvocationContext.builder().invocationId(UUID.randomUUID()).build();
    }
}
