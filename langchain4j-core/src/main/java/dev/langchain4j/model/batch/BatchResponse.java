package dev.langchain4j.model.batch;

import static dev.langchain4j.model.batch.BatchState.EXPIRED;
import static dev.langchain4j.model.batch.BatchState.FAILED;
import static dev.langchain4j.model.batch.BatchState.SUCCEEDED;

import dev.langchain4j.Experimental;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Represents the responses of a batch operation.
 *
 * <p>A batch response contains the batch identifier, current state, and optionally the results
 * when the batch has completed successfully.</p>
 *
 * @param <T> the type of the responses payload (e.g., {@code ChatResponse}, {@code Embedding})
 */
@Experimental
public class BatchResponse<T> {
    private static final List<BatchState> TERMINAL_BATCH_STATES = List.of(EXPIRED, FAILED, SUCCEEDED);

    private final BatchId batchId;
    private final BatchState state;
    private final List<T> responses;
    private final List<BatchError> errors;

    public BatchResponse(
            BatchId batchId, BatchState state, @Nullable List<T> responses, @Nullable List<BatchError> errors) {
        this.batchId = Objects.requireNonNull(batchId, "batchId cannot be null");
        this.state = Objects.requireNonNull(state, "state cannot be null");
        this.responses = responses;
        this.errors = errors;
    }

    /**
     * Returns the unique identifier for this batch operation.
     */
    public BatchId batchId() {
        return batchId;
    }

    /**
     * Returns the current state of the batch job.
     */
    public BatchState state() {
        return state;
    }

    /**
     * Returns the batch results, or {@code null} if the batch has not completed successfully.
     */
    @Nullable
    public List<T> responses() {
        return responses;
    }

    /**
     * Returns the errors encountered during the batch processing, if any.
     */
    @Nullable
    public List<BatchError> errors() {
        return errors;
    }

    /**
     * Returns {@code true} if the batch is still processing (not in a terminal state).
     */
    public boolean isInProgress() {
        return !TERMINAL_BATCH_STATES.contains(state);
    }

    /**
     * Returns {@code true} if the batch completed successfully.
     */
    public boolean hasSucceeded() {
        return state == SUCCEEDED;
    }

    /**
     * Returns {@code true} if the batch failed.
     */
    public boolean hasFailed() {
        return state == FAILED;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BatchResponse<?> that = (BatchResponse<?>) o;
        return Objects.equals(batchId, that.batchId)
                && state == that.state
                && Objects.equals(responses, that.responses)
                && Objects.equals(errors, that.errors);
    }

    @Override
    public int hashCode() {
        return Objects.hash(batchId, state, responses, errors);
    }

    @Override
    public String toString() {
        return "BatchResponse{" + "batchId="
                + batchId + ", state="
                + state + ", responses="
                + responses + ", errors="
                + errors + '}';
    }
}
