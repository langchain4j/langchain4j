package dev.langchain4j.experimental.durable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.experimental.durable.store.InMemoryTaskExecutionStore;
import dev.langchain4j.experimental.durable.store.event.TaskEvent;
import dev.langchain4j.experimental.durable.store.event.TaskRetryEvent;
import dev.langchain4j.experimental.durable.task.CheckpointPolicy;
import dev.langchain4j.experimental.durable.task.RetryPolicy;
import dev.langchain4j.experimental.durable.task.TaskConfiguration;
import dev.langchain4j.experimental.durable.task.TaskHandle;
import dev.langchain4j.experimental.durable.task.TaskId;
import dev.langchain4j.experimental.durable.task.TaskMetadata;
import dev.langchain4j.experimental.durable.task.TaskPausedException;
import dev.langchain4j.experimental.durable.task.TaskStatus;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LongLivedTaskServiceTest {

    private InMemoryTaskExecutionStore store;
    private LongLivedTaskService service;

    @BeforeEach
    void setup() {
        store = new InMemoryTaskExecutionStore();
        service = LongLivedTaskService.builder()
                .store(store)
                .defaultCheckpointPolicy(CheckpointPolicy.NONE)
                .build();
    }

    @Test
    void should_start_and_complete_task() throws Exception {
        TaskConfiguration config =
                TaskConfiguration.builder().agentName("testAgent").build();

        TaskHandle handle = service.start(config, () -> "result");

        Object result = handle.awaitResult().get(5, TimeUnit.SECONDS);
        assertThat(result).isEqualTo("result");
        assertThat(handle.status()).isEqualTo(TaskStatus.COMPLETED);
    }

    @Test
    void should_record_task_as_failed_on_exception() throws Exception {
        TaskConfiguration config =
                TaskConfiguration.builder().agentName("failAgent").build();

        TaskHandle handle = service.start(config, () -> {
            throw new RuntimeException("boom");
        });

        assertThatThrownBy(() -> handle.awaitResult().get(5, TimeUnit.SECONDS))
                .hasCauseInstanceOf(RuntimeException.class);

        // Wait briefly for async status update
        Thread.sleep(100);
        assertThat(handle.status()).isEqualTo(TaskStatus.FAILED);
    }

    @Test
    void should_pause_task_on_task_paused_exception() throws Exception {
        TaskConfiguration config =
                TaskConfiguration.builder().agentName("pauseAgent").build();

        TaskHandle handle = service.start(config, () -> {
            throw new TaskPausedException("Need approval", "approvalKey");
        });

        // Wait for async processing
        Thread.sleep(200);
        assertThat(handle.status()).isEqualTo(TaskStatus.PAUSED);
    }

    @Test
    void should_cancel_task() throws Exception {
        TaskConfiguration config =
                TaskConfiguration.builder().agentName("cancelAgent").build();

        TaskHandle handle = service.start(config, () -> {
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "never";
        });

        // Wait for task to start
        Thread.sleep(100);
        boolean cancelled = service.cancel(handle.id());

        assertThat(cancelled).isTrue();
    }

    @Test
    void should_list_tasks() throws Exception {
        TaskConfiguration config =
                TaskConfiguration.builder().agentName("listAgent").build();

        TaskHandle handle = service.start(config, () -> "done");
        handle.awaitResult().get(5, TimeUnit.SECONDS);

        assertThat(service.listTasks()).contains(handle.id());
    }

    @Test
    void should_get_task_events() throws Exception {
        TaskConfiguration config =
                TaskConfiguration.builder().agentName("eventAgent").build();

        TaskHandle handle = service.start(config, () -> "ok");
        handle.awaitResult().get(5, TimeUnit.SECONDS);

        assertThat(service.events(handle.id())).isNotEmpty();
    }

    @Test
    void should_cleanup_completed_task() throws Exception {
        TaskConfiguration config =
                TaskConfiguration.builder().agentName("cleanupAgent").build();

        TaskHandle handle = service.start(config, () -> "done");
        handle.awaitResult().get(5, TimeUnit.SECONDS);

        // Wait for status update
        Thread.sleep(100);
        boolean cleaned = service.cleanup(handle.id());

        assertThat(cleaned).isTrue();
        assertThat(service.metadata(handle.id())).isEmpty();
    }

    @Test
    void should_not_cleanup_running_task() {
        TaskConfiguration config =
                TaskConfiguration.builder().agentName("runningAgent").build();

        TaskHandle handle = service.start(config, () -> {
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        });

        // Wait for task to start
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assertThatThrownBy(() -> service.cleanup(handle.id())).isInstanceOf(IllegalStateException.class);

        service.cancel(handle.id());
    }

    @Test
    void should_require_store_in_builder() {
        assertThatThrownBy(() -> LongLivedTaskService.builder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("store");
    }

    // ---- Retry tests ----

    @Test
    void should_retry_on_transient_failure_then_succeed() throws Exception {
        AtomicInteger attempts = new AtomicInteger(0);

        RetryPolicy retryPolicy = RetryPolicy.builder()
                .maxRetries(3)
                .initialDelay(Duration.ofMillis(50))
                .retryableException(IOException.class)
                .build();

        TaskConfiguration config = TaskConfiguration.builder()
                .agentName("retryAgent")
                .retryPolicy(retryPolicy)
                .build();

        TaskHandle handle = service.start(config, () -> {
            int attempt = attempts.incrementAndGet();
            if (attempt < 3) {
                throw new RuntimeException(new IOException("Connection refused"));
            }
            return "success after retries";
        });

        Object result = handle.awaitResult().get(10, TimeUnit.SECONDS);
        assertThat(result).isEqualTo("success after retries");
        assertThat(handle.status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    void should_exhaust_retries_then_fail() throws Exception {
        RetryPolicy retryPolicy = RetryPolicy.builder()
                .maxRetries(2)
                .initialDelay(Duration.ofMillis(50))
                .build();

        TaskConfiguration config = TaskConfiguration.builder()
                .agentName("failRetryAgent")
                .retryPolicy(retryPolicy)
                .build();

        TaskHandle handle = service.start(config, () -> {
            throw new RuntimeException("always fails");
        });

        assertThatThrownBy(() -> handle.awaitResult().get(10, TimeUnit.SECONDS))
                .hasCauseInstanceOf(RuntimeException.class);

        Thread.sleep(100);
        assertThat(handle.status()).isEqualTo(TaskStatus.FAILED);

        // Verify retry events were recorded
        List<TaskEvent> events = service.events(handle.id());
        long retryEventCount =
                events.stream().filter(e -> e instanceof TaskRetryEvent).count();
        assertThat(retryEventCount).isEqualTo(2);
    }

    @Test
    void should_not_retry_task_paused_exception() throws Exception {
        RetryPolicy retryPolicy = RetryPolicy.builder()
                .maxRetries(3)
                .initialDelay(Duration.ofMillis(50))
                .build();

        TaskConfiguration config = TaskConfiguration.builder()
                .agentName("pauseRetryAgent")
                .retryPolicy(retryPolicy)
                .build();

        TaskHandle handle = service.start(config, () -> {
            throw new TaskPausedException("Need input", "inputKey");
        });

        Thread.sleep(200);
        assertThat(handle.status()).isEqualTo(TaskStatus.PAUSED);
    }

    // ---- Crash recovery tests ----

    @Test
    void should_recover_interrupted_tasks() {
        // Simulate tasks that were RUNNING when process crashed
        TaskId task1 = TaskId.random();
        TaskId task2 = TaskId.random();
        TaskMetadata meta1 = TaskMetadata.create(task1, "agent1", java.util.Map.of());
        TaskMetadata meta2 = TaskMetadata.create(task2, "agent2", java.util.Map.of());
        meta1.transitionTo(TaskStatus.RUNNING);
        meta2.transitionTo(TaskStatus.RUNNING);
        store.saveMetadata(meta1);
        store.saveMetadata(meta2);

        Set<TaskId> recovered = service.recoverInterruptedTasks();

        assertThat(recovered).containsExactlyInAnyOrder(task1, task2);
        assertThat(service.status(task1)).contains(TaskStatus.PAUSED);
        assertThat(service.status(task2)).contains(TaskStatus.PAUSED);
    }

    @Test
    void should_recover_retrying_tasks() {
        TaskId taskId = TaskId.random();
        TaskMetadata metadata = TaskMetadata.create(taskId, "retryingAgent", java.util.Map.of());
        metadata.transitionTo(TaskStatus.RETRYING);
        store.saveMetadata(metadata);

        Set<TaskId> recovered = service.recoverInterruptedTasks();

        assertThat(recovered).contains(taskId);
        assertThat(service.status(taskId)).contains(TaskStatus.PAUSED);
    }

    // ---- provideInput tests ----

    @Test
    void should_provide_input_for_paused_task() throws Exception {
        TaskConfiguration config =
                TaskConfiguration.builder().agentName("inputAgent").build();

        TaskHandle handle = service.start(config, () -> {
            throw new TaskPausedException("Need approval", "approvalKey");
        });

        Thread.sleep(200);
        assertThat(handle.status()).isEqualTo(TaskStatus.PAUSED);

        // Should not throw
        service.provideInput(handle.id(), "approvalKey", "approved");

        // Verify event was recorded
        List<TaskEvent> events = service.events(handle.id());
        assertThat(events).isNotEmpty();
    }

    @Test
    void should_reject_input_for_non_paused_task() throws Exception {
        TaskConfiguration config =
                TaskConfiguration.builder().agentName("doneAgent").build();

        TaskHandle handle = service.start(config, () -> "done");
        handle.awaitResult().get(5, TimeUnit.SECONDS);

        Thread.sleep(100);

        assertThatThrownBy(() -> service.provideInput(handle.id(), "key", "value"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not paused");
    }
}
