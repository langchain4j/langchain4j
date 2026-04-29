package dev.langchain4j.model.batch;

import java.util.List;

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
    UNSPECIFIED;

    private static final List<BatchState> TERMINAL_BATCH_STATES = List.of(EXPIRED, FAILED, SUCCEEDED);

    public boolean isTerminal() {
        return TERMINAL_BATCH_STATES.contains(this);
    }
}
