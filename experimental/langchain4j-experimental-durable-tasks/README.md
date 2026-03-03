# LangChain4j Experimental: Durable Long-Lived Tasks

An experimental module that adds durable lifecycle management to langchain4j agentic workflows.

## Features

- **Start / Monitor / Cancel** — submit agent workflows as managed tasks with unique identifiers
- **Pause / Resume** — human-in-the-loop and external-input scenarios; tasks persist their state and resume from the last checkpoint
- **Automatic Retry** — configurable exponential-backoff retry for transient failures
- **Crash Recovery** — recover tasks that were running when the process stopped
- **Event Journal** — full audit trail of every agent invocation, pause, retry, and completion
- **Checkpointing** — scope-aware snapshots enable fast resume without re-executing completed steps
- **Replay** — `ReplayingPlanner` replays completed invocations from the journal on resume

## Quick Start

```java
// 1. Create a store (in-memory for dev, file-based for production)
TaskExecutionStore store = new InMemoryTaskExecutionStore();

// 2. Build the service
LongLivedTaskService service = LongLivedTaskService.builder()
        .store(store)
        .build();

// 3. Start a task
TaskHandle handle = service.start(
        TaskConfiguration.builder().agentName("myWorkflow").build(),
        () -> myAgent.run(inputs));

// 4. Wait for the result
Object result = handle.awaitResult().get(5, TimeUnit.MINUTES);
```

## Pause and Resume (Human-in-the-Loop)

```java
// Use DurableHumanInTheLoop as an agent in your topology
DurableHumanInTheLoop approval = DurableHumanInTheLoop.builder()
        .outputKey("managerApproval")
        .reason("Waiting for manager approval")
        .build();

// When the agent runs and no input is available, the task pauses automatically.
// Later, provide the input and resume:
service.provideInput(taskId, "managerApproval", "approved");
TaskHandle resumed = service.resume(taskId, () -> myAgent.run(inputs));
```

## Automatic Retry

```java
RetryPolicy retry = RetryPolicy.builder()
        .maxRetries(3)
        .initialDelay(Duration.ofSeconds(2))
        .retryableException(IOException.class)
        .build();

TaskConfiguration config = TaskConfiguration.builder()
        .agentName("apiCaller")
        .retryPolicy(retry)
        .build();

TaskHandle handle = service.start(config, () -> callExternalApi());
```

## Crash Recovery

```java
// On application startup:
Set<TaskId> interrupted = service.recoverInterruptedTasks();
for (TaskId id : interrupted) {
    service.resume(id, () -> rebuildWorkflow(id));
}
```

## Persistence

Two built-in store implementations:

| Store | Use case |
|-------|----------|
| `InMemoryTaskExecutionStore` | Testing and development |
| `FileTaskExecutionStore` | Single-node production (file-per-task with atomic writes) |

Custom stores (e.g., database-backed) can be created by implementing `TaskExecutionStore`.

## Module Structure

| Package | Description |
|---------|-------------|
| `durable` | `LongLivedTaskService` — the central service |
| `durable.task` | Task API: `TaskId`, `TaskHandle`, `TaskStatus`, `TaskConfiguration`, `RetryPolicy` |
| `durable.store` | `TaskExecutionStore` SPI and implementations |
| `durable.store.event` | Event journal types (sealed hierarchy) |
| `durable.hitl` | `DurableHumanInTheLoop` agent |
| `durable.replay` | `ReplayingPlanner` for resume replay |
| `durable.journal` | `JournalingAgentListener` for event recording |

## Status

This module is **experimental**. All public types are annotated with `@Experimental` and
the API may change without notice in future releases.
