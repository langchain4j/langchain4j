package dev.langchain4j.experimental.durable;

import dev.langchain4j.Experimental;
import dev.langchain4j.experimental.durable.internal.CheckpointManager;
import dev.langchain4j.experimental.durable.internal.DefaultTaskHandle;
import dev.langchain4j.experimental.durable.internal.ObjectMapperFactory;
import dev.langchain4j.experimental.durable.journal.JournalingAgentListener;
import dev.langchain4j.experimental.durable.replay.ReplayingPlanner;
import dev.langchain4j.experimental.durable.store.TaskExecutionStore;
import dev.langchain4j.experimental.durable.store.event.TaskCancelledEvent;
import dev.langchain4j.experimental.durable.store.event.TaskCompletedEvent;
import dev.langchain4j.experimental.durable.store.event.TaskEvent;
import dev.langchain4j.experimental.durable.store.event.TaskFailedEvent;
import dev.langchain4j.experimental.durable.store.event.TaskPausedEvent;
import dev.langchain4j.experimental.durable.store.event.TaskResumedEvent;
import dev.langchain4j.experimental.durable.store.event.TaskRetryEvent;
import dev.langchain4j.experimental.durable.store.event.TaskStartedEvent;
import dev.langchain4j.experimental.durable.task.CheckpointPolicy;
import dev.langchain4j.experimental.durable.task.RetryPolicy;
import dev.langchain4j.experimental.durable.task.TaskConfiguration;
import dev.langchain4j.experimental.durable.task.TaskHandle;
import dev.langchain4j.experimental.durable.task.TaskId;
import dev.langchain4j.experimental.durable.task.TaskMetadata;
import dev.langchain4j.experimental.durable.task.TaskPausedException;
import dev.langchain4j.experimental.durable.task.TaskStatus;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central service for managing durable, long-lived agent tasks.
 *
 * <p>This service wraps any agentic execution with a durable lifecycle:
 * start, monitor, pause, resume, cancel, retry, and query. It is not limited
 * to any single workflow pattern — it supports sequential, conditional,
 * and loop-based planners equally well.
 *
 * <h2>Supported scenarios</h2>
 * <ul>
 *   <li>Long-running tool calls (APIs, web scraping, batch processing)</li>
 *   <li>Multi-step agentic workflows (sequential / conditional / loop)</li>
 *   <li>Crash and restart recovery</li>
 *   <li>Automatic retry after transient failures</li>
 *   <li>Explicit pause / resume (including human-in-the-loop)</li>
 *   <li>Observability and replay via the event journal</li>
 * </ul>
 *
 * <h2>Starting a task</h2>
 * <pre>{@code
 * TaskHandle handle = service.start(
 *     TaskConfiguration.builder().agentName("myWorkflow").build(),
 *     () -> myAgent.run(inputs));
 * }</pre>
 *
 * <h2>Resuming after crash or pause</h2>
 * <pre>{@code
 * // Application recreates the agent topology
 * TaskHandle handle = service.resume(taskId, () -> myAgent.run(inputs));
 * }</pre>
 *
 * <h2>Automatic retry on transient failures</h2>
 * <pre>{@code
 * RetryPolicy retry = RetryPolicy.builder()
 *         .maxRetries(3)
 *         .initialDelay(Duration.ofSeconds(2))
 *         .retryableException(IOException.class)
 *         .build();
 * TaskConfiguration config = TaskConfiguration.builder()
 *         .agentName("apiCaller")
 *         .retryPolicy(retry)
 *         .build();
 * TaskHandle handle = service.start(config, () -> callExternalApi());
 * }</pre>
 *
 * <h2>Crash recovery on startup</h2>
 * <pre>{@code
 * Set<TaskId> interrupted = service.recoverInterruptedTasks();
 * for (TaskId id : interrupted) {
 *     service.resume(id, () -> rebuildWorkflow(id));
 * }
 * }</pre>
 *
 * <h2>Providing external input for a paused task</h2>
 * <pre>{@code
 * service.provideInput(taskId, "approvalKey", "approved");
 * TaskHandle handle = service.resume(taskId, () -> myAgent.run(inputs));
 * }</pre>
 *
 * <p>The service uses a {@link TaskExecutionStore} for persistence and a
 * {@link JournalingAgentListener} to record execution events.
 *
 * @see TaskHandle
 * @see TaskExecutionStore
 * @see RetryPolicy
 */
@Experimental
public class LongLivedTaskService implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(LongLivedTaskService.class);

    private final TaskExecutionStore store;
    private final ExecutorService executorService;
    private final CheckpointPolicy defaultCheckpointPolicy;
    private final CheckpointManager checkpointManager;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final java.util.concurrent.ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<TaskId, DefaultTaskHandle> activeHandles = new ConcurrentHashMap<>();

    private LongLivedTaskService(Builder builder) {
        this.store = builder.store;
        this.executorService = builder.executorService != null
                ? builder.executorService
                : Executors.newCachedThreadPool(r -> {
                    Thread t = new Thread(r);
                    t.setDaemon(true);
                    t.setName("durable-task-" + t.getId());
                    return t;
                });
        this.defaultCheckpointPolicy = builder.defaultCheckpointPolicy;
        this.checkpointManager = new CheckpointManager(store);
        this.objectMapper = ObjectMapperFactory.create();
        this.scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("durable-task-timeout-scheduler");
            return t;
        });
    }

    // ---- Lifecycle operations ----

    /**
     * Starts a new durable task.
     *
     * <p>The task is assigned a random identifier, persisted as PENDING, then
     * submitted for asynchronous execution. The returned {@link TaskHandle} can
     * be used to monitor progress or cancel the task.
     *
     * @param configuration the task configuration (including optional retry policy)
     * @param workflow      supplier that invokes the agent topology and returns the result
     * @return a handle to the running task
     */
    public TaskHandle start(TaskConfiguration configuration, Supplier<Object> workflow) {
        TaskId taskId = TaskId.random();
        TaskMetadata metadata = TaskMetadata.create(taskId, configuration.agentName(), configuration.labels());
        store.saveMetadata(metadata);

        CompletableFuture<Object> future = new CompletableFuture<>();
        DefaultTaskHandle handle = new DefaultTaskHandle(taskId, metadata, future);
        handle.setCancelCallback(this::cancel);
        activeHandles.put(taskId, handle);

        CheckpointPolicy policy =
                configuration.checkpointPolicy() != null ? configuration.checkpointPolicy() : defaultCheckpointPolicy;
        RetryPolicy retryPolicy = configuration.retryPolicy();
        Duration timeout = configuration.timeout();

        store.appendEvent(new TaskStartedEvent(taskId, Instant.now(), Map.of()));

        executorService.submit(() -> executeTask(taskId, metadata, workflow, future, policy, retryPolicy, timeout));

        LOG.info("Task {} started with agent '{}'", taskId, configuration.agentName());
        return handle;
    }

    /**
     * Resumes a previously paused, failed, or interrupted task.
     *
     * <p>The application must recreate the agent topology and provide it as a
     * {@code workflow} supplier. The caller is responsible for wiring a
     * {@link ReplayingPlanner} into the workflow if replay of completed steps
     * is desired — the journal events can be obtained via {@link #events(TaskId)}.
     *
     * <p>Failed tasks can be resumed as a manual retry mechanism. Completed and
     * cancelled tasks cannot be resumed.
     *
     * <p>If the task is paused waiting for external input, call
     * {@link #provideInput(TaskId, String, Object)} first, then resume.
     *
     * @param taskId   the identifier of the task to resume
     * @param workflow supplier that invokes the (recreated) agent topology
     * @return a handle to the resumed task
     * @throws IllegalStateException if the task is not found, completed or cancelled
     */
    public TaskHandle resume(TaskId taskId, Supplier<Object> workflow) {
        return resume(taskId, workflow, null);
    }

    /**
     * Resumes a previously paused, failed, or interrupted task with explicit configuration.
     *
     * <p>Allows specifying a {@link TaskConfiguration} to override the retry policy and
     * checkpoint policy for the resumed execution. If {@code configuration} is null,
     * defaults are used.
     *
     * @param taskId        the identifier of the task to resume
     * @param workflow      supplier that invokes the (recreated) agent topology
     * @param configuration optional configuration override for the resumed execution
     * @return a handle to the resumed task
     * @throws IllegalStateException if the task is not found, completed or cancelled
     */
    public TaskHandle resume(TaskId taskId, Supplier<Object> workflow, TaskConfiguration configuration) {
        TaskMetadata metadata =
                store.loadMetadata(taskId).orElseThrow(() -> new IllegalStateException("Task not found: " + taskId));

        TaskStatus current = metadata.status();
        if (current == TaskStatus.COMPLETED || current == TaskStatus.CANCELLED) {
            throw new IllegalStateException("Cannot resume task " + taskId + " in state: " + current);
        }
        if (current == TaskStatus.RUNNING || current == TaskStatus.RETRYING) {
            throw new IllegalStateException("Cannot resume task " + taskId + " that is already " + current
                    + "; cancel it first or wait for it to finish");
        }

        // Store-atomic CAS to activate the task. This prevents concurrent resumes
        // from both succeeding and launching duplicate workers.
        Optional<TaskMetadata> updated = store.compareAndSetStatus(taskId, current, TaskStatus.RUNNING);
        if (updated.isEmpty()) {
            throw new IllegalStateException(
                    "Cannot resume task " + taskId + ": concurrent transition already in progress");
        }
        TaskMetadata activatedMetadata = updated.get();

        store.appendEvent(new TaskResumedEvent(taskId, Instant.now(), Map.of()));

        CompletableFuture<Object> future = new CompletableFuture<>();
        DefaultTaskHandle handle = new DefaultTaskHandle(taskId, activatedMetadata, future);
        handle.setCancelCallback(this::cancel);

        // Complete old handle's future so callers holding it don't hang forever
        DefaultTaskHandle oldHandle = activeHandles.put(taskId, handle);
        if (oldHandle != null) {
            oldHandle
                    .future()
                    .completeExceptionally(
                            new IllegalStateException("Task " + taskId + " was resumed; use the new handle"));
        }

        CheckpointPolicy policy = (configuration != null && configuration.checkpointPolicy() != null)
                ? configuration.checkpointPolicy()
                : defaultCheckpointPolicy;
        RetryPolicy retryPolicy = (configuration != null) ? configuration.retryPolicy() : null;
        Duration timeout = (configuration != null) ? configuration.timeout() : null;

        executorService.submit(
                () -> executeTask(taskId, activatedMetadata, workflow, future, policy, retryPolicy, timeout));

        LOG.info("Task {} resumed", taskId);
        return handle;
    }

    /**
     * Writes external input into the event journal for a paused task.
     *
     * <p>The input is recorded as key-value data in a {@link TaskResumedEvent}.
     * When the task is subsequently {@link #resume resumed}, the replay mechanism
     * writes this value into the scope so agents that depend on it (e.g.,
     * DurableHumanInTheLoop) find it immediately.
     *
     * <p>This is a general mechanism — not limited to human input. Any external
     * data (API callbacks, approval signals, computed values) can be provided.
     *
     * @param taskId the task identifier
     * @param key    the scope key under which the value will be stored
     * @param value  the external value
     * @throws IllegalStateException if the task is not found or not paused
     */
    public void provideInput(TaskId taskId, String key, Object value) {
        TaskMetadata metadata =
                store.loadMetadata(taskId).orElseThrow(() -> new IllegalStateException("Task not found: " + taskId));

        if (metadata.status() != TaskStatus.PAUSED) {
            throw new IllegalStateException(
                    "Task " + taskId + " is not paused (current status: " + metadata.status() + ")");
        }

        store.appendEvent(new TaskResumedEvent(taskId, Instant.now(), Map.of(key, value)));

        LOG.info("External input provided for task {} at key '{}'", taskId, key);
    }

    /**
     * Cancels a task.
     *
     * <p>If the task is currently running, the underlying future is cancelled
     * with an interrupt. If the task is paused or retrying, it transitions
     * directly to {@link TaskStatus#CANCELLED}. Uses an atomic compare-and-set
     * guard to prevent races with concurrent completion.
     *
     * @param taskId the task identifier
     * @return {@code true} if the task was cancelled, {@code false} if already terminal
     */
    public boolean cancel(TaskId taskId) {
        TaskMetadata metadata = store.loadMetadata(taskId).orElse(null);
        if (metadata == null) {
            return false;
        }

        // Store-atomic CAS: try to transition from whatever non-terminal state to CANCELLED.
        // The store-level CAS loads the persisted metadata, checks the status, transitions,
        // and saves atomically — preventing the race where cancel and completion operate on
        // different in-memory instances with FileTaskExecutionStore.
        TaskStatus current = metadata.status();
        if (current.isTerminal()) {
            return false;
        }
        Optional<TaskMetadata> cancelled = store.compareAndSetStatus(taskId, current, TaskStatus.CANCELLED);
        if (cancelled.isEmpty()) {
            // Status changed concurrently — re-read from store and try once more
            metadata = store.loadMetadata(taskId).orElse(null);
            if (metadata == null) {
                return false;
            }
            current = metadata.status();
            if (current.isTerminal()) {
                return false;
            }
            cancelled = store.compareAndSetStatus(taskId, current, TaskStatus.CANCELLED);
        }
        if (cancelled.isEmpty()) {
            // Both CAS attempts failed — another transition won the race
            return false;
        }

        store.appendEvent(new TaskCancelledEvent(taskId, Instant.now()));

        // Cancel the future AFTER metadata transition
        DefaultTaskHandle handle = activeHandles.remove(taskId);
        if (handle != null) {
            // Sync in-memory metadata for handle.status() callers
            handle.updateMetadata(cancelled.get());
            handle.future()
                    .completeExceptionally(
                            new java.util.concurrent.CancellationException("Task " + taskId + " cancelled"));
            handle.cancel();
        }

        LOG.info("Task {} cancelled", taskId);
        return true;
    }

    // ---- Crash recovery ----

    /**
     * Discovers tasks that were running when the process last stopped.
     *
     * <p>This method finds all tasks in {@link TaskStatus#RUNNING} or
     * {@link TaskStatus#RETRYING} status and transitions them to
     * {@link TaskStatus#PAUSED} so they can be explicitly resumed by the
     * application. This is the recommended first call on startup when using
     * a persistent {@link TaskExecutionStore}.
     *
     * <p>The caller is responsible for resuming each returned task by calling
     * {@link #resume(TaskId, Supplier)} with a recreated workflow.
     *
     * @return the set of task ids that were interrupted and are now paused
     */
    public Set<TaskId> recoverInterruptedTasks() {
        Set<TaskId> running = store.getTaskIdsByStatus(TaskStatus.RUNNING);
        Set<TaskId> retrying = store.getTaskIdsByStatus(TaskStatus.RETRYING);

        List<TaskId> interrupted = new ArrayList<>();
        interrupted.addAll(running);
        interrupted.addAll(retrying);

        List<TaskId> recovered = new ArrayList<>();
        for (TaskId taskId : interrupted) {
            store.loadMetadata(taskId).ifPresent(metadata -> {
                TaskStatus current = metadata.status();
                Optional<TaskMetadata> paused = store.compareAndSetStatus(taskId, current, TaskStatus.PAUSED);
                if (paused.isPresent()) {
                    store.appendEvent(
                            new TaskPausedEvent(taskId, Instant.now(), "Recovered after process restart", null));
                    recovered.add(taskId);
                    LOG.info("Task {} recovered from interrupted state, now PAUSED", taskId);
                } else {
                    LOG.warn("Task {} could not be recovered (concurrent status transition)", taskId);
                }
            });
        }

        return Set.copyOf(recovered);
    }

    // ---- Query operations ----

    /**
     * Returns the current status of a task.
     *
     * @param taskId the task identifier
     * @return the task status, or empty if not found
     */
    public Optional<TaskStatus> status(TaskId taskId) {
        return store.loadMetadata(taskId).map(TaskMetadata::status);
    }

    /**
     * Returns the metadata for a task.
     *
     * @param taskId the task identifier
     * @return the task metadata, or empty if not found
     */
    public Optional<TaskMetadata> metadata(TaskId taskId) {
        return store.loadMetadata(taskId);
    }

    /**
     * Returns the event journal for a task. The journal provides a complete
     * audit trail of the task's execution: starts, agent invocations,
     * retries, pauses, failures, and completion.
     *
     * @param taskId the task identifier
     * @return ordered list of events
     */
    public List<TaskEvent> events(TaskId taskId) {
        return store.loadEvents(taskId);
    }

    /**
     * Returns all known task identifiers.
     *
     * @return set of task ids
     */
    public Set<TaskId> listTasks() {
        return store.getAllTaskIds();
    }

    /**
     * Returns task identifiers filtered by status.
     *
     * @param status the status to filter by
     * @return set of matching task ids
     */
    public Set<TaskId> listTasks(TaskStatus status) {
        return store.getTaskIdsByStatus(status);
    }

    /**
     * Deletes all data for a terminal task.
     *
     * @param taskId the task identifier
     * @return {@code true} if the task was deleted
     * @throws IllegalStateException if the task exists but is not in a terminal state
     */
    public boolean cleanup(TaskId taskId) {
        TaskMetadata metadata = store.loadMetadata(taskId).orElse(null);
        if (metadata != null && !metadata.status().isTerminal()) {
            throw new IllegalStateException(
                    "Cannot cleanup task " + taskId + " in non-terminal state: " + metadata.status());
        }
        activeHandles.remove(taskId);
        return store.delete(taskId);
    }

    // ---- Factory helpers ----

    /**
     * Creates a {@link JournalingAgentListener} for a task. This listener should be
     * registered with the agent topology to enable event journaling.
     *
     * @param taskId the task identifier
     * @return a new journaling listener
     */
    public JournalingAgentListener createJournalingListener(TaskId taskId) {
        return new JournalingAgentListener(taskId, store);
    }

    /**
     * Creates a {@link JournalingAgentListener} for a task with checkpoint-policy
     * awareness. When the policy is {@link CheckpointPolicy#AFTER_EACH_AGENT},
     * a checkpoint with the current {@link dev.langchain4j.agentic.scope.AgenticScope}
     * is taken after every successful agent invocation. When the policy is
     * {@link CheckpointPolicy#AFTER_ROOT_CALL}, a scope-aware checkpoint is taken
     * when the root agentic scope is destroyed (i.e., the root call completes).
     *
     * @param taskId the task identifier
     * @param policy the checkpoint policy to apply
     * @return a new journaling listener
     */
    public JournalingAgentListener createJournalingListener(TaskId taskId, CheckpointPolicy policy) {
        java.util.function.Consumer<dev.langchain4j.agentic.scope.AgenticScope> afterAgentCheckpoint = null;
        java.util.function.Consumer<dev.langchain4j.agentic.scope.AgenticScope> rootCallCheckpoint = null;

        if (policy == CheckpointPolicy.AFTER_EACH_AGENT) {
            afterAgentCheckpoint = (scope) -> {
                store.loadMetadata(taskId).ifPresent(md -> checkpointManager.checkpoint(taskId, md, scope));
            };
        }
        if (policy == CheckpointPolicy.AFTER_ROOT_CALL || policy == CheckpointPolicy.AFTER_EACH_AGENT) {
            rootCallCheckpoint = (scope) -> {
                store.loadMetadata(taskId).ifPresent(md -> checkpointManager.checkpoint(taskId, md, scope));
            };
        }
        return new JournalingAgentListener(taskId, store, afterAgentCheckpoint, rootCallCheckpoint);
    }

    /**
     * Returns the underlying store.
     *
     * @return the task execution store
     */
    public TaskExecutionStore store() {
        return store;
    }

    // ---- Shutdown ----

    /**
     * Gracefully shuts down the executor service and scheduler, waiting up to the given
     * timeout for running tasks to complete. Tasks that do not finish within the timeout
     * are interrupted.
     *
     * @param timeout  the maximum time to wait
     * @param unit     the time unit
     * @throws InterruptedException if the current thread is interrupted while waiting
     */
    public void shutdown(long timeout, TimeUnit unit) throws InterruptedException {
        LOG.info("Shutting down LongLivedTaskService (timeout={}{})", timeout, unit);
        scheduler.shutdown();
        executorService.shutdown();
        if (!executorService.awaitTermination(timeout, unit)) {
            LOG.warn("Executor did not terminate within timeout, forcing shutdown");
            executorService.shutdownNow();
        }
        if (!scheduler.awaitTermination(Math.min(timeout, 5), unit)) {
            scheduler.shutdownNow();
        }
    }

    /**
     * Implements {@link AutoCloseable} for try-with-resources support.
     * Shuts down with a default timeout of 30 seconds.
     */
    @Override
    public void close() {
        try {
            shutdown(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Interrupted during close, forcing shutdown");
            executorService.shutdownNow();
            scheduler.shutdownNow();
        }
    }

    // ---- Internal execution ----

    private void executeTask(
            TaskId taskId,
            TaskMetadata metadata,
            Supplier<Object> workflow,
            CompletableFuture<Object> future,
            CheckpointPolicy policy,
            RetryPolicy retryPolicy,
            Duration timeout) {

        // Schedule a timeout cancellation if configured
        java.util.concurrent.ScheduledFuture<?> timeoutFuture = null;
        if (timeout != null && !timeout.isZero() && !timeout.isNegative()) {
            timeoutFuture = scheduler.schedule(
                    () -> {
                        LOG.warn("Task {} timed out after {}", taskId, timeout);
                        cancel(taskId);
                    },
                    timeout.toMillis(),
                    java.util.concurrent.TimeUnit.MILLISECONDS);
        }

        try {
            doExecuteTask(taskId, metadata, workflow, future, policy, retryPolicy);
        } finally {
            if (timeoutFuture != null) {
                timeoutFuture.cancel(false);
            }
        }
    }

    private void doExecuteTask(
            TaskId taskId,
            TaskMetadata metadata,
            Supplier<Object> workflow,
            CompletableFuture<Object> future,
            CheckpointPolicy policy,
            RetryPolicy retryPolicy) {

        int maxAttempts = (retryPolicy != null) ? retryPolicy.maxRetries() + 1 : 1;
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                // Store-atomic CAS guard: only transition to RUNNING if we're in an expected
                // pre-run state. If cancel() won the race, the store has CANCELLED — bail out.
                TaskStatus current = metadata.status();
                if (current.isTerminal()) {
                    LOG.debug("Task {} is already in terminal state {}, aborting execution", taskId, current);
                    return;
                }
                Optional<TaskMetadata> running = store.compareAndSetStatus(taskId, current, TaskStatus.RUNNING);
                if (running.isEmpty()) {
                    // Status changed concurrently — re-check via store
                    TaskMetadata fresh = store.loadMetadata(taskId).orElse(null);
                    if (fresh == null || fresh.status().isTerminal()) {
                        return;
                    }
                    running = store.compareAndSetStatus(taskId, fresh.status(), TaskStatus.RUNNING);
                    if (running.isEmpty()) {
                        // Both CAS attempts failed — bail out rather than force a stale write
                        fresh = store.loadMetadata(taskId).orElse(null);
                        if (fresh == null || fresh.status().isTerminal()) {
                            return;
                        }
                        LOG.warn(
                                "Task {} CAS to RUNNING failed twice (current: {}), aborting attempt",
                                taskId,
                                fresh.status());
                        return;
                    } else {
                        metadata = running.get();
                    }
                } else {
                    metadata = running.get();
                }
                // Sync the handle's metadata view
                DefaultTaskHandle currentHandle = activeHandles.get(taskId);
                if (currentHandle != null) {
                    currentHandle.updateMetadata(metadata);
                }

                Object result = workflow.get();

                // Store-atomic CAS: only move to COMPLETED if the persisted status is
                // still RUNNING. cancel() may have written CANCELLED to the store while
                // workflow.get() was executing — without store-level CAS, the worker's
                // stale in-memory metadata would overwrite CANCELLED with COMPLETED.
                Optional<TaskMetadata> completed =
                        store.compareAndSetStatus(taskId, TaskStatus.RUNNING, TaskStatus.COMPLETED);
                if (completed.isEmpty()) {
                    LOG.info(
                            "Task {} finished but cannot transition to COMPLETED (current: {})",
                            taskId,
                            metadata.status());
                    // The task was cancelled mid-flight — don't overwrite the cancel.
                    // Still complete the future so callers don't hang.
                    future.complete(result);
                    return;
                }
                metadata = completed.get();

                String serializedResult = serializeResult(result);
                store.appendEvent(new TaskCompletedEvent(taskId, Instant.now(), serializedResult));

                // NOTE: no service-level completion checkpoint here. The scope-aware
                // checkpoint is already created by JournalingAgentListener.
                // beforeAgenticScopeDestroyed (wired for both AFTER_ROOT_CALL and
                // AFTER_EACH_AGENT) fires before workflow.get() returns. A null-scope
                // checkpoint here would overwrite that scope data.

                future.complete(result);
                activeHandles.remove(taskId);
                LOG.info("Task {} completed successfully", taskId);
                return;

            } catch (TaskPausedException e) {
                Optional<TaskMetadata> paused =
                        store.compareAndSetStatus(taskId, TaskStatus.RUNNING, TaskStatus.PAUSED);
                if (paused.isEmpty()) {
                    LOG.info("Task {} paused but status already changed (concurrent cancel?)", taskId);
                    return;
                }
                metadata = paused.get();

                store.appendEvent(new TaskPausedEvent(taskId, Instant.now(), e.reason(), e.pendingOutputKey()));

                if (policy == CheckpointPolicy.AFTER_EACH_AGENT) {
                    checkpointManager.checkpoint(taskId, metadata, null);
                }

                // Complete the future exceptionally so callers don't hang.
                // They can detect pause via the TaskPausedException type.
                future.completeExceptionally(e);
                activeHandles.remove(taskId);
                LOG.info("Task {} paused: {}", taskId, e.reason());
                return;

            } catch (Exception e) {
                lastException = e;

                // Check if the exception wraps a TaskPausedException (e.g., from CompletionException)
                TaskPausedException pausedException = findInCauseChain(e, TaskPausedException.class);
                if (pausedException != null) {
                    Optional<TaskMetadata> paused =
                            store.compareAndSetStatus(taskId, TaskStatus.RUNNING, TaskStatus.PAUSED);
                    if (paused.isEmpty()) {
                        LOG.info("Task {} paused (wrapped) but status already changed (concurrent cancel?)", taskId);
                        return;
                    }
                    metadata = paused.get();
                    store.appendEvent(new TaskPausedEvent(
                            taskId, Instant.now(), pausedException.reason(), pausedException.pendingOutputKey()));
                    if (policy == CheckpointPolicy.AFTER_EACH_AGENT) {
                        checkpointManager.checkpoint(taskId, metadata, null);
                    }
                    future.completeExceptionally(pausedException);
                    activeHandles.remove(taskId);
                    LOG.info(
                            "Task {} paused (unwrapped from {}): {}",
                            taskId,
                            e.getClass().getSimpleName(),
                            pausedException.reason());
                    return;
                }

                // If already cancelled, don't retry or record failure.
                // Check the store (not just in-memory metadata) to see concurrent cancel.
                TaskStatus storedStatus =
                        store.loadMetadata(taskId).map(TaskMetadata::status).orElse(metadata.status());
                if (storedStatus.isTerminal()) {
                    LOG.debug("Task {} caught exception but already terminal ({})", taskId, storedStatus);
                    future.completeExceptionally(e);
                    activeHandles.remove(taskId);
                    return;
                }

                boolean canRetry = attempt < maxAttempts && retryPolicy != null && retryPolicy.isRetryable(e);
                if (canRetry) {
                    Duration delay = retryPolicy.delayForAttempt(attempt);
                    store.appendEvent(new TaskRetryEvent(
                            taskId,
                            Instant.now(),
                            attempt,
                            retryPolicy.maxRetries(),
                            e.getMessage(),
                            delay.toMillis()));

                    LOG.warn(
                            "Task {} attempt {}/{} failed ({}), retrying in {}ms",
                            taskId,
                            attempt,
                            maxAttempts,
                            e.getMessage(),
                            delay.toMillis());

                    Optional<TaskMetadata> retrying =
                            store.compareAndSetStatus(taskId, TaskStatus.RUNNING, TaskStatus.RETRYING);
                    if (retrying.isPresent()) {
                        metadata = retrying.get();
                    }

                    if (policy == CheckpointPolicy.AFTER_EACH_AGENT) {
                        checkpointManager.checkpoint(taskId, metadata, null);
                    }

                    try {
                        Thread.sleep(delay.toMillis());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        if (store.compareAndSetStatus(taskId, TaskStatus.RETRYING, TaskStatus.CANCELLED)
                                .isPresent()) {
                            store.appendEvent(new TaskCancelledEvent(taskId, Instant.now()));
                        }
                        future.cancel(true);
                        activeHandles.remove(taskId);
                        LOG.info("Task {} interrupted during retry delay, cancelled", taskId);
                        return;
                    }
                    // Continue loop for next attempt
                } else {
                    break; // Fall through to terminal failure
                }
            }
        }

        // All attempts exhausted — record terminal failure.
        // Use RUNNING as expected because that's the state we entered the last attempt with.
        // If cancel() already set CANCELLED, RUNNING won't match and the CAS will correctly fail.
        if (lastException != null) {
            Optional<TaskMetadata> failed = store.compareAndSetStatus(
                    taskId, TaskStatus.RUNNING, TaskStatus.FAILED, lastException.getMessage());
            if (failed.isPresent()) {
                metadata = failed.get();

                String stackTrace = formatStackTrace(lastException);
                store.appendEvent(new TaskFailedEvent(taskId, Instant.now(), lastException.getMessage(), stackTrace));
            }

            future.completeExceptionally(lastException);
            activeHandles.remove(taskId);
            LOG.error("Task {} failed: {}", taskId, lastException.getMessage(), lastException);
        }
    }

    private static String formatStackTrace(Throwable throwable) {
        StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    /**
     * Walks the exception cause chain looking for an instance of the given type.
     *
     * @param throwable the starting throwable
     * @param type      the type to search for
     * @param <T>       the exception type
     * @return the matching exception, or {@code null} if not found
     */
    @SuppressWarnings("unchecked")
    private static <T extends Throwable> T findInCauseChain(Throwable throwable, Class<T> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return (T) current;
            }
            current = current.getCause();
        }
        return null;
    }

    /**
     * Serializes a task result using Jackson. Falls back to {@code toString()} if
     * serialization fails.
     */
    private String serializeResult(Object result) {
        if (result == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            LOG.warn("Failed to serialize task result, falling back to toString(): {}", e.getMessage());
            return result.toString();
        }
    }

    /**
     * Creates a new builder for {@link LongLivedTaskService}.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link LongLivedTaskService}.
     */
    public static final class Builder {

        private TaskExecutionStore store;
        private ExecutorService executorService;
        private CheckpointPolicy defaultCheckpointPolicy = CheckpointPolicy.AFTER_EACH_AGENT;

        private Builder() {}

        /**
         * Sets the task execution store. Required.
         *
         * @param store the store for task persistence
         * @return this builder
         */
        public Builder store(TaskExecutionStore store) {
            this.store = store;
            return this;
        }

        /**
         * Sets a custom executor service for running tasks.
         * If not set, a cached thread pool with daemon threads is used.
         *
         * @param executorService the executor service
         * @return this builder
         */
        public Builder executorService(ExecutorService executorService) {
            this.executorService = executorService;
            return this;
        }

        /**
         * Sets the default checkpoint policy for tasks that don't specify one.
         * Defaults to {@link CheckpointPolicy#AFTER_EACH_AGENT}.
         *
         * @param defaultCheckpointPolicy the default policy
         * @return this builder
         */
        public Builder defaultCheckpointPolicy(CheckpointPolicy defaultCheckpointPolicy) {
            this.defaultCheckpointPolicy = defaultCheckpointPolicy;
            return this;
        }

        /**
         * Builds the {@link LongLivedTaskService}.
         *
         * @return a new long-lived task service
         * @throws IllegalArgumentException if store is null
         */
        public LongLivedTaskService build() {
            if (store == null) {
                throw new IllegalArgumentException("store must not be null");
            }
            return new LongLivedTaskService(this);
        }
    }
}
