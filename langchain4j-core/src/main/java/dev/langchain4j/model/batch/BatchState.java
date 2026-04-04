package dev.langchain4j.model.batch;

/**
 * Represents the possible states of a batch job.
 */
public enum BatchState {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    EXPIRED,
    UNSPECIFIED
}
