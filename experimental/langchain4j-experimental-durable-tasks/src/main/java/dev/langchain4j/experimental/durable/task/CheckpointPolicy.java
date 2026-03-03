package dev.langchain4j.experimental.durable.task;

import dev.langchain4j.Experimental;

/**
 * Controls when checkpoints (scope snapshots) are saved during task execution.
 *
 * <p>Checkpoints enable resuming a task from the last saved point after a crash or restart.
 * More frequent checkpoints provide better resume granularity at the cost of storage I/O.
 */
@Experimental
public enum CheckpointPolicy {

    /** No checkpoints are saved. Resume is not possible. */
    NONE,

    /**
     * A checkpoint is saved when the root agentic scope is about to be destroyed,
     * capturing the final scope state. Intermediate transitions such as pause or retry
     * do not trigger a checkpoint under this policy.
     */
    AFTER_ROOT_CALL,

    /**
     * A checkpoint is saved after each agent invocation completes, capturing the
     * current scope state. This is the default and provides the finest resume
     * granularity.
     */
    AFTER_EACH_AGENT
}
