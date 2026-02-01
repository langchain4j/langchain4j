package dev.langchain4j.model.batch;

import static dev.langchain4j.model.batch.BatchJobState.BATCH_STATE_EXPIRED;
import static dev.langchain4j.model.batch.BatchJobState.BATCH_STATE_FAILED;
import static dev.langchain4j.model.batch.BatchJobState.BATCH_STATE_SUCCEEDED;

import java.util.List;
import dev.langchain4j.Experimental;
import org.jspecify.annotations.Nullable;

/**
 * Represents the response of a batch operation.
 *
 * <p>A batch response contains the batch identifier, current state, and optionally the results
 * when the batch has completed successfully.</p>
 *
 * @param <T>       the type of the response payload (e.g., {@code List<ChatResponse>}, {@code List<Embedding>})
 * @param batchName the unique identifier for this batch operation
 * @param state     the current state of the batch job
 * @param response  the batch results, or {@code null} if the batch has not completed successfully
 */
@Experimental
public record BatchResponse<T>(BatchName batchName, BatchJobState state, List<T> response) {
    private static final List<BatchJobState> TERMINAL_BATCH_STATES = List.of(
            BATCH_STATE_EXPIRED, BATCH_STATE_FAILED, BATCH_STATE_SUCCEEDED
    );

    /**
     * Returns {@code true} if the batch is still processing (not in a terminal state).
     */
    public boolean isIncomplete() {
        return !TERMINAL_BATCH_STATES.contains(state);
    }

    /**
     * Returns {@code true} if the batch completed successfully.
     */
    public boolean isSuccess() {
        return state == BATCH_STATE_SUCCEEDED;
    }

    /**
     * Returns {@code true} if the batch failed.
     */
    public boolean isError() {
        return state == BATCH_STATE_FAILED;
    }
}
