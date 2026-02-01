package dev.langchain4j.model.batch;

/**
 * Represents the possible states of a batch job.
 */
public enum BatchJobState {
    BATCH_STATE_PENDING,
    BATCH_STATE_RUNNING,
    BATCH_STATE_SUCCEEDED,
    BATCH_STATE_FAILED,
    BATCH_STATE_CANCELLED,
    BATCH_STATE_EXPIRED,
    UNSPECIFIED
}
