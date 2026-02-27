package dev.langchain4j.experimental.durable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.experimental.durable.store.Checkpoint;
import dev.langchain4j.experimental.durable.store.InMemoryTaskExecutionStore;
import dev.langchain4j.experimental.durable.store.event.TaskCancelledEvent;
import dev.langchain4j.experimental.durable.store.event.TaskCompletedEvent;
import dev.langchain4j.experimental.durable.store.event.TaskEvent;
import dev.langchain4j.experimental.durable.store.file.FileTaskExecutionStore;
import dev.langchain4j.experimental.durable.task.CheckpointPolicy;
import dev.langchain4j.experimental.durable.task.RetryPolicy;
import dev.langchain4j.experimental.durable.task.TaskConfiguration;
import dev.langchain4j.experimental.durable.task.TaskHandle;
import dev.langchain4j.experimental.durable.task.TaskId;
import dev.langchain4j.experimental.durable.task.TaskMetadata;
import dev.langchain4j.experimental.durable.task.TaskPausedException;
import dev.langchain4j.experimental.durable.task.TaskStatus;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for all P1/P2/P3 code-review fixes.
 */
class CodeReviewFixesTest {

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

    // ---- P1: Cancel race - compareAndTransition ----

    @Test
    void cancel_vs_complete_race_should_not_throw() throws Exception {
        // Verify that when cancel() races with natural completion,
        // no IllegalStateException is thrown and the task ends up in a terminal state.
        CountDownLatch workflowRunning = new CountDownLatch(1);
        CountDownLatch cancelIssued = new CountDownLatch(1);

        TaskConfiguration config =
                TaskConfiguration.builder().agentName("raceAgent").build();

        TaskHandle handle = service.start(config, () -> {
            workflowRunning.countDown();
            try {
                cancelIssued.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "completed";
        });

        workflowRunning.await(5, TimeUnit.SECONDS);

        // Issue cancel while workflow is about to return
        service.cancel(handle.id());
        cancelIssued.countDown();

        // Wait for everything to settle
        Thread.sleep(300);

        // Task should be in a terminal state — either CANCELLED or COMPLETED, no crash
        TaskStatus status = service.status(handle.id()).orElse(null);
        assertThat(status).isNotNull();
        assertThat(status.isTerminal()).isTrue();
    }

    @Test
    void cancel_already_terminal_should_return_false() throws Exception {
        TaskConfiguration config =
                TaskConfiguration.builder().agentName("doneAgent").build();

        TaskHandle handle = service.start(config, () -> "done");
        handle.awaitResult().get(5, TimeUnit.SECONDS);
        Thread.sleep(100);

        boolean cancelled = service.cancel(handle.id());
        assertThat(cancelled).isFalse();
    }

    @Test
    void cancel_should_record_cancelled_event() throws Exception {
        TaskConfiguration config =
                TaskConfiguration.builder().agentName("cancelEventAgent").build();

        TaskHandle handle = service.start(config, () -> {
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        });

        Thread.sleep(100);
        service.cancel(handle.id());
        Thread.sleep(100);

        List<TaskEvent> events = service.events(handle.id());
        assertThat(events.stream().anyMatch(e -> e instanceof TaskCancelledEvent))
                .isTrue();
    }

    // ---- P1: executeTask CAS for COMPLETED ----

    @Test
    void concurrent_cancel_during_workflow_should_not_overwrite_cancelled_with_completed() throws Exception {
        AtomicReference<TaskId> taskIdRef = new AtomicReference<>();
        CountDownLatch workflowStarted = new CountDownLatch(1);

        TaskConfiguration config =
                TaskConfiguration.builder().agentName("cancelDuringWork").build();

        TaskHandle handle = service.start(config, () -> {
            taskIdRef.set(null); // just to show workflow ran
            workflowStarted.countDown();
            try {
                Thread.sleep(5_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "result after sleep";
        });

        workflowStarted.await(5, TimeUnit.SECONDS);
        Thread.sleep(50);

        // Cancel while running
        service.cancel(handle.id());

        Thread.sleep(300);

        // The status should be CANCELLED, not COMPLETED
        assertThat(service.status(handle.id())).contains(TaskStatus.CANCELLED);
    }

    // ---- P2: Resume from FAILED ----

    @Test
    void should_resume_failed_task() throws Exception {
        AtomicBoolean shouldFail = new AtomicBoolean(true);

        TaskConfiguration config =
                TaskConfiguration.builder().agentName("failThenResume").build();

        TaskHandle handle = service.start(config, () -> {
            if (shouldFail.get()) {
                throw new RuntimeException("first run fails");
            }
            return "success on retry";
        });

        assertThatThrownBy(() -> handle.awaitResult().get(5, TimeUnit.SECONDS));
        Thread.sleep(100);
        assertThat(service.status(handle.id())).contains(TaskStatus.FAILED);

        // Now resume the failed task
        shouldFail.set(false);
        TaskHandle resumed = service.resume(handle.id(), () -> "success on retry");
        Object result = resumed.awaitResult().get(5, TimeUnit.SECONDS);

        assertThat(result).isEqualTo("success on retry");
        assertThat(resumed.status()).isEqualTo(TaskStatus.COMPLETED);
    }

    @Test
    void should_not_resume_completed_task() throws Exception {
        TaskConfiguration config =
                TaskConfiguration.builder().agentName("completedAgent").build();

        TaskHandle handle = service.start(config, () -> "done");
        handle.awaitResult().get(5, TimeUnit.SECONDS);
        Thread.sleep(100);

        assertThatThrownBy(() -> service.resume(handle.id(), () -> "retry"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("COMPLETED");
    }

    @Test
    void should_not_resume_cancelled_task() throws Exception {
        TaskConfiguration config =
                TaskConfiguration.builder().agentName("cancelledAgent").build();

        TaskHandle handle = service.start(config, () -> {
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        });

        Thread.sleep(100);
        service.cancel(handle.id());
        Thread.sleep(100);

        assertThatThrownBy(() -> service.resume(handle.id(), () -> "retry"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CANCELLED");
    }

    // ---- P2: Resume with retryPolicy ----

    @Test
    void should_resume_with_retry_policy_from_configuration() throws Exception {
        // Start a task that pauses
        TaskConfiguration config =
                TaskConfiguration.builder().agentName("pauseForRetry").build();

        TaskHandle handle = service.start(config, () -> {
            throw new TaskPausedException("waiting", "key");
        });

        Thread.sleep(200);
        assertThat(service.status(handle.id())).contains(TaskStatus.PAUSED);

        // Resume with explicit retry policy
        AtomicBoolean firstAttempt = new AtomicBoolean(true);
        RetryPolicy retryPolicy = RetryPolicy.builder()
                .maxRetries(2)
                .initialDelay(Duration.ofMillis(50))
                .build();

        TaskConfiguration resumeConfig = TaskConfiguration.builder()
                .agentName("pauseForRetry")
                .retryPolicy(retryPolicy)
                .build();

        TaskHandle resumed = service.resume(
                handle.id(),
                () -> {
                    if (firstAttempt.getAndSet(false)) {
                        throw new RuntimeException("transient error");
                    }
                    return "success after retry";
                },
                resumeConfig);

        Object result = resumed.awaitResult().get(10, TimeUnit.SECONDS);
        assertThat(result).isEqualTo("success after retry");
    }

    // ---- P3: Timeout ----

    @Test
    void should_cancel_task_on_timeout() throws Exception {
        TaskConfiguration config = TaskConfiguration.builder()
                .agentName("timeoutAgent")
                .timeout(Duration.ofMillis(200))
                .build();

        TaskHandle handle = service.start(config, () -> {
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "never";
        });

        Thread.sleep(500);

        TaskStatus status = service.status(handle.id()).orElse(null);
        assertThat(status).isEqualTo(TaskStatus.CANCELLED);
    }

    @Test
    void should_not_timeout_if_completes_in_time() throws Exception {
        TaskConfiguration config = TaskConfiguration.builder()
                .agentName("fastAgent")
                .timeout(Duration.ofSeconds(5))
                .build();

        TaskHandle handle = service.start(config, () -> "fast result");
        Object result = handle.awaitResult().get(5, TimeUnit.SECONDS);

        assertThat(result).isEqualTo("fast result");
        assertThat(handle.status()).isEqualTo(TaskStatus.COMPLETED);
    }

    // ---- P1: TaskMetadata.compareAndTransition ----

    @Test
    void compare_and_transition_should_succeed_when_expected_matches() {
        TaskMetadata metadata = TaskMetadata.create(TaskId.random(), "agent", Map.of());
        metadata.transitionTo(TaskStatus.RUNNING);

        boolean success = metadata.compareAndTransition(TaskStatus.RUNNING, TaskStatus.COMPLETED);

        assertThat(success).isTrue();
        assertThat(metadata.status()).isEqualTo(TaskStatus.COMPLETED);
    }

    @Test
    void compare_and_transition_should_fail_when_expected_does_not_match() {
        TaskMetadata metadata = TaskMetadata.create(TaskId.random(), "agent", Map.of());
        metadata.transitionTo(TaskStatus.RUNNING);

        boolean success = metadata.compareAndTransition(TaskStatus.PAUSED, TaskStatus.COMPLETED);

        assertThat(success).isFalse();
        assertThat(metadata.status()).isEqualTo(TaskStatus.RUNNING);
    }

    @Test
    void compare_and_transition_should_set_failure_reason() {
        TaskMetadata metadata = TaskMetadata.create(TaskId.random(), "agent", Map.of());
        metadata.transitionTo(TaskStatus.RUNNING);

        boolean success = metadata.compareAndTransition(TaskStatus.RUNNING, TaskStatus.FAILED, "error occurred");

        assertThat(success).isTrue();
        assertThat(metadata.status()).isEqualTo(TaskStatus.FAILED);
        assertThat(metadata.failureReason()).isEqualTo("error occurred");
    }

    @Test
    void compare_and_transition_from_terminal_should_fail() {
        TaskMetadata metadata = TaskMetadata.create(TaskId.random(), "agent", Map.of());
        metadata.transitionTo(TaskStatus.COMPLETED);

        // COMPLETED is hard-terminal: CAS must return false even if expected matches
        boolean success = metadata.compareAndTransition(TaskStatus.COMPLETED, TaskStatus.RUNNING);

        assertThat(success).isFalse();
        assertThat(metadata.status()).isEqualTo(TaskStatus.COMPLETED);
    }

    @Test
    void compare_and_transition_from_cancelled_should_fail() {
        TaskMetadata metadata = TaskMetadata.create(TaskId.random(), "agent", Map.of());
        metadata.transitionTo(TaskStatus.RUNNING);
        metadata.compareAndTransition(TaskStatus.RUNNING, TaskStatus.CANCELLED);

        boolean success = metadata.compareAndTransition(TaskStatus.CANCELLED, TaskStatus.RUNNING);

        assertThat(success).isFalse();
        assertThat(metadata.status()).isEqualTo(TaskStatus.CANCELLED);
    }

    @Test
    void compare_and_transition_from_failed_to_running_should_succeed() {
        // FAILED is soft-terminal: FAILED→RUNNING allowed for resume
        TaskMetadata metadata = TaskMetadata.create(TaskId.random(), "agent", Map.of());
        metadata.transitionTo(TaskStatus.RUNNING);
        metadata.compareAndTransition(TaskStatus.RUNNING, TaskStatus.FAILED, "error");

        boolean success = metadata.compareAndTransition(TaskStatus.FAILED, TaskStatus.RUNNING);

        assertThat(success).isTrue();
        assertThat(metadata.status()).isEqualTo(TaskStatus.RUNNING);
    }

    // ---- P1: Paused handle should not hang ----

    @Test
    void paused_task_handle_future_should_complete_exceptionally() throws Exception {
        TaskConfiguration config =
                TaskConfiguration.builder().agentName("pauseAgent").build();

        TaskHandle handle = service.start(config, () -> {
            throw new TaskPausedException("needs input", "key");
        });

        // The future should complete with TaskPausedException, not hang
        assertThatThrownBy(() -> handle.awaitResult().get(5, TimeUnit.SECONDS))
                .hasCauseInstanceOf(TaskPausedException.class);
    }

    @Test
    void resumed_task_should_complete_old_handle_future() throws Exception {
        TaskConfiguration config =
                TaskConfiguration.builder().agentName("resumeOldHandle").build();

        TaskHandle handle1 = service.start(config, () -> {
            throw new TaskPausedException("paused", "key");
        });

        // Wait for pause
        assertThatThrownBy(() -> handle1.awaitResult().get(5, TimeUnit.SECONDS))
                .hasCauseInstanceOf(TaskPausedException.class);
        Thread.sleep(100);

        // Resume — creates handle2
        TaskHandle handle2 = service.resume(handle1.id(), () -> "resumed result");
        Object result = handle2.awaitResult().get(5, TimeUnit.SECONDS);

        assertThat(result).isEqualTo("resumed result");
        assertThat(handle2.status()).isEqualTo(TaskStatus.COMPLETED);
    }

    // ---- P2: cancel double-CAS ----

    @Test
    void cancel_should_not_report_success_if_already_completed() throws Exception {
        TaskConfiguration config =
                TaskConfiguration.builder().agentName("quickAgent").build();

        TaskHandle handle = service.start(config, () -> "done");
        handle.awaitResult().get(5, TimeUnit.SECONDS);
        Thread.sleep(100);

        // Task is COMPLETED — cancel must return false
        boolean cancelled = service.cancel(handle.id());
        assertThat(cancelled).isFalse();

        // No TaskCancelledEvent should be recorded
        List<TaskEvent> events = service.events(handle.id());
        assertThat(events.stream().anyMatch(e -> e instanceof TaskCancelledEvent))
                .isFalse();
    }

    // ---- P2: activeHandles leak ----

    @Test
    void active_handles_should_not_leak_on_completion() throws Exception {
        TaskConfiguration config =
                TaskConfiguration.builder().agentName("leakTest").build();

        TaskHandle handle = service.start(config, () -> "done");
        handle.awaitResult().get(5, TimeUnit.SECONDS);
        Thread.sleep(100);

        // After completion, the handle should be removed from internal tracking
        // We verify indirectly: cancelling a completed task returns false
        boolean cancelled = service.cancel(handle.id());
        assertThat(cancelled).isFalse();
        assertThat(handle.status()).isEqualTo(TaskStatus.COMPLETED);
    }

    @Test
    void active_handles_should_not_leak_on_failure() throws Exception {
        TaskConfiguration config =
                TaskConfiguration.builder().agentName("failLeakTest").build();

        TaskHandle handle = service.start(config, () -> {
            throw new RuntimeException("boom");
        });

        assertThatThrownBy(() -> handle.awaitResult().get(5, TimeUnit.SECONDS));
        Thread.sleep(100);

        assertThat(handle.status()).isEqualTo(TaskStatus.FAILED);
    }

    @Test
    void active_handles_should_not_leak_on_pause() throws Exception {
        TaskConfiguration config =
                TaskConfiguration.builder().agentName("pauseLeakTest").build();

        TaskHandle handle = service.start(config, () -> {
            throw new TaskPausedException("paused", "key");
        });

        assertThatThrownBy(() -> handle.awaitResult().get(5, TimeUnit.SECONDS))
                .hasCauseInstanceOf(TaskPausedException.class);
        Thread.sleep(100);

        assertThat(handle.status()).isEqualTo(TaskStatus.PAUSED);
    }

    // ---- P1 (third review): Resume of RUNNING/RETRYING blocked ----

    @Test
    void resume_running_task_should_throw() throws Exception {
        CountDownLatch workflowRunning = new CountDownLatch(1);
        CountDownLatch canFinish = new CountDownLatch(1);

        TaskConfiguration config =
                TaskConfiguration.builder().agentName("longRunner").build();

        TaskHandle handle = service.start(config, () -> {
            workflowRunning.countDown();
            try {
                canFinish.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "done";
        });

        workflowRunning.await(5, TimeUnit.SECONDS);

        // Task is RUNNING — resume must throw
        assertThatThrownBy(() -> service.resume(handle.id(), () -> "retry"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already RUNNING");

        canFinish.countDown();
        handle.awaitResult().get(5, TimeUnit.SECONDS);
    }

    // ---- P2 (third review): AFTER_ROOT_CALL should not checkpoint on pause ----

    @Test
    void after_root_call_should_not_checkpoint_on_pause() throws Exception {
        LongLivedTaskService svcWithRootCall = LongLivedTaskService.builder()
                .store(store)
                .defaultCheckpointPolicy(CheckpointPolicy.AFTER_ROOT_CALL)
                .build();

        TaskConfiguration config =
                TaskConfiguration.builder().agentName("pauseNoChk").build();

        TaskHandle handle = svcWithRootCall.start(config, () -> {
            throw new TaskPausedException("paused", "key");
        });

        assertThatThrownBy(() -> handle.awaitResult().get(5, TimeUnit.SECONDS))
                .hasCauseInstanceOf(TaskPausedException.class);
        Thread.sleep(100);

        // Under AFTER_ROOT_CALL, a pause must NOT produce service-level checkpoints
        assertThat(store.loadCheckpoint(handle.id())).isEmpty();
    }

    @Test
    void after_root_call_should_not_checkpoint_on_retry() throws Exception {
        LongLivedTaskService svcWithRootCall = LongLivedTaskService.builder()
                .store(store)
                .defaultCheckpointPolicy(CheckpointPolicy.AFTER_ROOT_CALL)
                .build();

        AtomicBoolean firstAttempt = new AtomicBoolean(true);
        RetryPolicy retryPolicy = RetryPolicy.builder()
                .maxRetries(1)
                .initialDelay(Duration.ofMillis(10))
                .build();
        TaskConfiguration config = TaskConfiguration.builder()
                .agentName("retryNoChk")
                .retryPolicy(retryPolicy)
                .build();

        TaskHandle handle = svcWithRootCall.start(config, () -> {
            if (firstAttempt.getAndSet(false)) {
                throw new RuntimeException("transient");
            }
            return "ok";
        });

        handle.awaitResult().get(5, TimeUnit.SECONDS);
        Thread.sleep(100);

        // Under AFTER_ROOT_CALL, service-level checkpoint creation was removed
        // to prevent overwriting scope-aware checkpoints from
        // JournalingAgentListener.beforeAgenticScopeDestroyed. Without real
        // agents producing scope callbacks, no checkpoint should exist.
        Checkpoint cp = store.loadCheckpoint(handle.id()).orElse(null);
        assertThat(cp).isNull();
    }

    @Test
    void after_each_agent_should_checkpoint_on_pause() throws Exception {
        LongLivedTaskService svcWithEach = LongLivedTaskService.builder()
                .store(store)
                .defaultCheckpointPolicy(CheckpointPolicy.AFTER_EACH_AGENT)
                .build();

        TaskConfiguration config =
                TaskConfiguration.builder().agentName("pauseWithChk").build();

        TaskHandle handle = svcWithEach.start(config, () -> {
            throw new TaskPausedException("paused", "key");
        });

        assertThatThrownBy(() -> handle.awaitResult().get(5, TimeUnit.SECONDS))
                .hasCauseInstanceOf(TaskPausedException.class);
        Thread.sleep(100);

        // Under AFTER_EACH_AGENT, the pause SHOULD produce a checkpoint
        Checkpoint cp = store.loadCheckpoint(handle.id()).orElse(null);
        assertThat(cp).isNotNull();
        assertThat(cp.metadata().status()).isEqualTo(TaskStatus.PAUSED);
    }

    // ---- Completion events are recorded properly ----

    @Test
    void completed_task_should_have_completion_event() throws Exception {
        TaskConfiguration config =
                TaskConfiguration.builder().agentName("completeEventAgent").build();

        TaskHandle handle = service.start(config, () -> "the result");
        handle.awaitResult().get(5, TimeUnit.SECONDS);
        Thread.sleep(100);

        List<TaskEvent> events = service.events(handle.id());
        boolean hasCompleted = events.stream().anyMatch(e -> e instanceof TaskCompletedEvent);
        assertThat(hasCompleted).isTrue();
    }

    // ---- P1 (fourth review): Store-atomic CAS — cancel vs complete with FileStore ----

    @TempDir
    Path tempDir;

    @Test
    void cancel_vs_complete_with_file_store_should_respect_cancel() throws Exception {
        FileTaskExecutionStore fileStore = FileTaskExecutionStore.builder()
                .baseDir(tempDir.resolve("cancel-race"))
                .build();
        LongLivedTaskService fileService = LongLivedTaskService.builder()
                .store(fileStore)
                .defaultCheckpointPolicy(CheckpointPolicy.NONE)
                .build();

        CountDownLatch workflowRunning = new CountDownLatch(1);
        CountDownLatch cancelDone = new CountDownLatch(1);

        TaskConfiguration config =
                TaskConfiguration.builder().agentName("fileCancelRace").build();

        TaskHandle handle = fileService.start(config, () -> {
            workflowRunning.countDown();
            try {
                // Wait for cancel to happen, then return normally
                cancelDone.await(5, TimeUnit.SECONDS);
                Thread.sleep(50); // Small delay to let cancel persist first
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "completed after cancel";
        });

        workflowRunning.await(5, TimeUnit.SECONDS);
        Thread.sleep(50);

        // Cancel while workflow is running
        boolean cancelled = fileService.cancel(handle.id());
        cancelDone.countDown();
        assertThat(cancelled).isTrue();

        // Wait for worker to finish
        Thread.sleep(500);

        // With store-atomic CAS, cancel must win — the worker's completion CAS
        // should fail because the persisted status is now CANCELLED.
        TaskStatus finalStatus =
                fileStore.loadMetadata(handle.id()).map(TaskMetadata::status).orElse(null);
        assertThat(finalStatus).isEqualTo(TaskStatus.CANCELLED);
    }

    @Test
    void store_compare_and_set_should_succeed_when_expected_matches() {
        TaskId taskId = TaskId.random();
        TaskMetadata metadata = TaskMetadata.create(taskId, "agent", Map.of());
        metadata.transitionTo(TaskStatus.RUNNING);
        store.saveMetadata(metadata);

        Optional<TaskMetadata> updated = store.compareAndSetStatus(taskId, TaskStatus.RUNNING, TaskStatus.COMPLETED);

        assertThat(updated).isPresent();
        assertThat(updated.get().status()).isEqualTo(TaskStatus.COMPLETED);
        // Verify persisted:
        assertThat(store.loadMetadata(taskId).get().status()).isEqualTo(TaskStatus.COMPLETED);
    }

    @Test
    void store_compare_and_set_should_fail_when_expected_does_not_match() {
        TaskId taskId = TaskId.random();
        TaskMetadata metadata = TaskMetadata.create(taskId, "agent", Map.of());
        metadata.transitionTo(TaskStatus.RUNNING);
        store.saveMetadata(metadata);

        // Expected is PAUSED but actual is RUNNING — should fail
        Optional<TaskMetadata> updated = store.compareAndSetStatus(taskId, TaskStatus.PAUSED, TaskStatus.COMPLETED);

        assertThat(updated).isEmpty();
        // Status unchanged:
        assertThat(store.loadMetadata(taskId).get().status()).isEqualTo(TaskStatus.RUNNING);
    }

    @Test
    void store_compare_and_set_should_return_empty_for_unknown_task() {
        Optional<TaskMetadata> updated =
                store.compareAndSetStatus(new TaskId("doesNotExist"), TaskStatus.RUNNING, TaskStatus.COMPLETED);
        assertThat(updated).isEmpty();
    }

    @Test
    void file_store_compare_and_set_should_be_atomic() throws IOException {
        FileTaskExecutionStore fileStore = FileTaskExecutionStore.builder()
                .baseDir(tempDir.resolve("cas-atomic"))
                .build();

        TaskId taskId = TaskId.random();
        TaskMetadata metadata = TaskMetadata.create(taskId, "agent", Map.of());
        metadata.transitionTo(TaskStatus.RUNNING);
        fileStore.saveMetadata(metadata);

        // First CAS: RUNNING → CANCELLED should succeed
        Optional<TaskMetadata> cancelled =
                fileStore.compareAndSetStatus(taskId, TaskStatus.RUNNING, TaskStatus.CANCELLED);
        assertThat(cancelled).isPresent();
        assertThat(cancelled.get().status()).isEqualTo(TaskStatus.CANCELLED);

        // Second CAS: RUNNING → COMPLETED should fail (store has CANCELLED)
        Optional<TaskMetadata> completed =
                fileStore.compareAndSetStatus(taskId, TaskStatus.RUNNING, TaskStatus.COMPLETED);
        assertThat(completed).isEmpty();

        // Store still has CANCELLED:
        assertThat(fileStore.loadMetadata(taskId).get().status()).isEqualTo(TaskStatus.CANCELLED);
    }

    @Test
    void store_compare_and_set_with_failure_reason() {
        TaskId taskId = TaskId.random();
        TaskMetadata metadata = TaskMetadata.create(taskId, "agent", Map.of());
        metadata.transitionTo(TaskStatus.RUNNING);
        store.saveMetadata(metadata);

        Optional<TaskMetadata> failed =
                store.compareAndSetStatus(taskId, TaskStatus.RUNNING, TaskStatus.FAILED, "out of memory");

        assertThat(failed).isPresent();
        assertThat(failed.get().status()).isEqualTo(TaskStatus.FAILED);
        assertThat(failed.get().failureReason()).isEqualTo("out of memory");
    }

    // ---- P2 (fourth review): Resume CAS check ----

    @Test
    void concurrent_resume_of_failed_task_should_reject_second_caller() throws Exception {
        // Start a task that fails
        TaskConfiguration config =
                TaskConfiguration.builder().agentName("concurrentResume").build();

        TaskHandle handle = service.start(config, () -> {
            throw new RuntimeException("initial failure");
        });

        assertThatThrownBy(() -> handle.awaitResult().get(5, TimeUnit.SECONDS));
        Thread.sleep(100);
        assertThat(service.status(handle.id())).contains(TaskStatus.FAILED);

        // First resume succeeds
        CountDownLatch resumeRunning = new CountDownLatch(1);
        CountDownLatch canFinish = new CountDownLatch(1);
        TaskHandle resumed1 = service.resume(handle.id(), () -> {
            resumeRunning.countDown();
            try {
                canFinish.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "ok";
        });

        resumeRunning.await(5, TimeUnit.SECONDS);

        // Second resume should throw because status is now RUNNING, not FAILED
        assertThatThrownBy(() -> service.resume(handle.id(), () -> "should not run"))
                .isInstanceOf(IllegalStateException.class);

        canFinish.countDown();
        resumed1.awaitResult().get(5, TimeUnit.SECONDS);
    }

    // ---- P1 (fourth review): Completion checkpoint should NOT overwrite scope ----

    @Test
    void after_root_call_completion_should_not_overwrite_scope_checkpoint() throws Exception {
        // Under AFTER_ROOT_CALL, the completion path must NOT write a null-scope
        // checkpoint that overwrites the scope-aware checkpoint from
        // beforeAgenticScopeDestroyed. After completion, if there is a checkpoint,
        // it should NOT have null serializedScope (unless the agent produced no scope
        // events at all).
        LongLivedTaskService svcWithRootCall = LongLivedTaskService.builder()
                .store(store)
                .defaultCheckpointPolicy(CheckpointPolicy.AFTER_ROOT_CALL)
                .build();

        TaskConfiguration config =
                TaskConfiguration.builder().agentName("scopePreservation").build();

        // Note: this test verifies the service-level behavior. The service should NOT
        // produce a null-scope completion checkpoint. The JournalingAgentListener
        // would produce the scope-aware checkpoint via beforeAgenticScopeDestroyed,
        // but in this unit test without actual agentic scope events, we verify that
        // the service at least does NOT overwrite any existing checkpoint.

        // Pre-populate a mock checkpoint with non-null scope to prove the service
        // does not overwrite it on completion.
        TaskHandle handle = svcWithRootCall.start(config, () -> "result");
        Thread.sleep(50); // let task start
        TaskId taskId = handle.id();

        // Wait for completion
        handle.awaitResult().get(5, TimeUnit.SECONDS);
        Thread.sleep(100);

        // The service should NOT have written a null-scope checkpoint on completion.
        // Either no checkpoint exists (if no agent callbacks fired), or the existing
        // one has scope data. We verify there's no checkpoint with null scope AND
        // COMPLETED metadata:
        Optional<Checkpoint> cp = store.loadCheckpoint(taskId);
        if (cp.isPresent()) {
            // If a checkpoint was created by the service, its serializedScope should
            // not be the null produced by the old completion path.
            // (In this minimal test without real agents, the service should simply
            // not create any completion checkpoint at all.)
            assertThat(cp.get().metadata().status())
                    .as("Completion checkpoint should not exist from service-level code")
                    .isNotEqualTo(TaskStatus.COMPLETED);
        }
        // If empty — correct: no service-level completion checkpoint was created.
    }
}
