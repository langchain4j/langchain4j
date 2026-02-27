package dev.langchain4j.experimental.durable.internal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.experimental.durable.task.TaskId;
import dev.langchain4j.experimental.durable.task.TaskMetadata;
import dev.langchain4j.experimental.durable.task.TaskStatus;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultTaskHandleTest {

    private TaskId taskId;
    private TaskMetadata metadata;
    private CompletableFuture<Object> future;
    private DefaultTaskHandle handle;

    @BeforeEach
    void setup() {
        taskId = TaskId.random();
        metadata = TaskMetadata.create(taskId, "testAgent", Map.of());
        future = new CompletableFuture<>();
        handle = new DefaultTaskHandle(taskId, metadata, future);
    }

    @Test
    void should_return_task_id() {
        assertThat(handle.id()).isEqualTo(taskId);
    }

    @Test
    void should_reflect_metadata_status() {
        assertThat(handle.status()).isEqualTo(TaskStatus.PENDING);

        metadata.transitionTo(TaskStatus.RUNNING);
        assertThat(handle.status()).isEqualTo(TaskStatus.RUNNING);
    }

    @Test
    void should_return_empty_result_when_not_completed() {
        assertThat(handle.result()).isEmpty();
    }

    @Test
    void should_return_result_when_future_completed() {
        future.complete("the result");

        assertThat(handle.result()).contains("the result");
    }

    @Test
    void should_return_empty_result_when_future_completed_exceptionally() {
        future.completeExceptionally(new RuntimeException("fail"));

        assertThat(handle.result()).isEmpty();
    }

    @Test
    void should_return_empty_result_when_future_cancelled() {
        future.cancel(true);

        assertThat(handle.result()).isEmpty();
    }

    @Test
    void should_return_the_future_from_await_result() {
        assertThat(handle.awaitResult()).isSameAs(future);
    }

    @Test
    void should_cancel_future_and_return_true() {
        metadata.transitionTo(TaskStatus.RUNNING);

        boolean cancelled = handle.cancel();

        assertThat(cancelled).isTrue();
        assertThat(future.isCancelled()).isTrue();
        // Note: metadata transitions are managed by LongLivedTaskService, not the handle.
        // The handle.cancel() only cancels the underlying future.
    }

    @Test
    void should_return_false_when_future_already_completed() {
        future.complete("done");

        boolean cancelled = handle.cancel();

        assertThat(cancelled).isFalse();
        assertThat(future.isCancelled()).isFalse();
    }

    @Test
    void should_update_status_after_update_metadata() {
        TaskMetadata updatedMetadata = TaskMetadata.create(taskId, "testAgent", Map.of());
        updatedMetadata.transitionTo(TaskStatus.PAUSED);

        handle.updateMetadata(updatedMetadata);

        assertThat(handle.status()).isEqualTo(TaskStatus.PAUSED);
    }

    @Test
    void should_expose_underlying_future() {
        assertThat(handle.future()).isSameAs(future);
    }
}
