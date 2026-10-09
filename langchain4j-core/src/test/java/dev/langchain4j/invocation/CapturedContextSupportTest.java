package dev.langchain4j.invocation;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.spi.CapturedContext;
import dev.langchain4j.spi.ExecutorProvider;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

/**
 * {@link ExecutorProvider#set(ExecutorProvider)} is process-wide, so this class runs alone and sequentially.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class CapturedContextSupportTest {

    private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();

    private static final UUID INVOCATION_ID = UUID.randomUUID();

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @AfterEach
    void cleanUp() {
        ExecutorProvider.set(null);
        REQUEST_ID.remove();
        executor.shutdownNow();
    }

    @Test
    void should_run_tasks_with_the_captured_context_on_another_thread() throws Exception {
        ExecutorProvider.set(new CapturingExecutorProvider());
        InvocationContext invocationContext = withRequestId("request-42", CapturedContextSupportTest::capturingInvocationContext);

        Executor restoring = CapturedContextSupport.restoringIn(executor, invocationContext);

        assertThat(CompletableFuture.supplyAsync(REQUEST_ID::get, restoring).get(10, SECONDS))
                .isEqualTo("request-42");
        assertThat(CompletableFuture.supplyAsync(REQUEST_ID::get, executor).get(10, SECONDS))
                .as("the captured context is only restored while the task runs")
                .isNull();
    }

    @Test
    void should_keep_the_captured_context_when_the_invocation_context_is_copied() throws Exception {
        ExecutorProvider.set(new CapturingExecutorProvider());
        InvocationContext invocationContext = withRequestId("request-42", CapturedContextSupportTest::capturingInvocationContext);

        InvocationContext copy = invocationContext.toBuilder().chatMemoryId("another").build();

        Executor restoring = CapturedContextSupport.restoringIn(executor, copy);
        assertThat(CompletableFuture.supplyAsync(REQUEST_ID::get, restoring).get(10, SECONDS))
                .isEqualTo("request-42");
    }

    @Test
    void should_not_change_the_public_state_of_the_invocation_context() {
        ExecutorProvider.set(new CapturingExecutorProvider());

        InvocationContext withCapturedContext =
                withRequestId("request-42", CapturedContextSupportTest::capturingInvocationContext);
        InvocationContext withoutCapturedContext =
                InvocationContext.builder().invocationId(INVOCATION_ID).build();

        assertThat(withCapturedContext.managedParameters()).isNull();
        assertThat(withCapturedContext).isEqualTo(withoutCapturedContext);
        assertThat(withCapturedContext).hasSameHashCodeAs(withoutCapturedContext);
        assertThat(withCapturedContext).hasToString(withoutCapturedContext.toString());
    }

    @Test
    void should_capture_nothing_without_an_executor_provider() {
        InvocationContext invocationContext = capturingInvocationContext();

        assertThat(CapturedContextSupport.restoringIn(executor, invocationContext)).isSameAs(executor);
    }

    @Test
    void should_capture_nothing_with_a_provider_that_does_not_capture_context() {
        // a provider written before captureContext() existed keeps the default
        ExecutorProvider.set(() -> executor);

        InvocationContext invocationContext = capturingInvocationContext();

        assertThat(CapturedContextSupport.restoringIn(executor, invocationContext)).isSameAs(executor);
    }

    @Test
    void should_leave_the_executor_unchanged_when_nothing_was_captured() {
        InvocationContext withoutCapturedContext = InvocationContext.builder().build();

        assertThat(CapturedContextSupport.restoringIn(executor, withoutCapturedContext)).isSameAs(executor);
        assertThat(CapturedContextSupport.restoringIn(executor, null)).isSameAs(executor);
        assertThat(CapturedContextSupport.restoringIn(null, withoutCapturedContext)).isNull();
    }

    @Test
    void should_fail_fast_when_the_provider_returns_null() {
        ExecutorProvider.set(new NullCapturingExecutorProvider());

        assertThatThrownBy(CapturedContextSupportTest::capturingInvocationContext)
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(NullCapturingExecutorProvider.class.getName() + ".captureContext()");
    }

    private static InvocationContext capturingInvocationContext() {
        return CapturedContextSupport.captureInto(InvocationContext.builder().invocationId(INVOCATION_ID))
                .build();
    }

    private static <T> T withRequestId(String requestId, java.util.function.Supplier<T> call) {
        REQUEST_ID.set(requestId);
        try {
            return call.get();
        } finally {
            REQUEST_ID.remove();
        }
    }

    private static class CapturingExecutorProvider implements ExecutorProvider {

        @Override
        public Executor executor() {
            return null;
        }

        @Override
        public CapturedContext captureContext() {
            String captured = REQUEST_ID.get();
            return task -> () -> {
                String previous = REQUEST_ID.get();
                REQUEST_ID.set(captured);
                try {
                    task.run();
                } finally {
                    REQUEST_ID.set(previous);
                }
            };
        }
    }

    private static class NullCapturingExecutorProvider implements ExecutorProvider {

        @Override
        public Executor executor() {
            return null;
        }

        @Override
        public CapturedContext captureContext() {
            return null;
        }
    }
}
