package dev.langchain4j.model.batch;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.model.batch.BatchState.FAILED;
import static dev.langchain4j.model.batch.BatchState.SUCCEEDED;

import dev.langchain4j.Experimental;
import java.util.List;
import java.util.Objects;

/**
 * Represents the responses of a batch operation.
 *
 * <p>A batch response contains the batch identifier, current state, and optionally the results
 * when the batch has completed successfully.</p>
 *
 * @param <T> the type of the responses payload (e.g., {@code ChatResponse}, {@code Embedding})
 */
// TODO: responses() and errors() are returned as two uncorrelated lists, so for a partially-failed
//  batch the caller cannot tell which input request produced which response or error. Consider
//  preserving per-request identity (e.g. the request key for file-based batches, or the original
//  index for inline batches) so results can be mapped back to their originating requests.
// TODO: expose batch-level metadata (creation/completion timestamps, request counts, model name)
//  once a provider-agnostic representation is agreed upon.
@Experimental
public class BatchResponse<T> {

    private final String batchId;
    private final BatchState state;
    private final List<T> responses;
    private final List<BatchError> errors;

    public BatchResponse(Builder<T> builder) {
        this.batchId = ensureNotNull(builder.batchId, "batchId");
        this.state = ensureNotNull(builder.state, "state");
        this.responses = copy(builder.responses);
        this.errors = copy(builder.errors);
    }

    /**
     * Returns the unique identifier for this batch operation.
     */
    public String batchId() {
        return batchId;
    }

    /**
     * Returns the current state of the batch job.
     */
    public BatchState state() {
        return state;
    }

    /**
     * Returns the batch results, or an empty list if the batch has not completed successfully.
     */
    public List<T> responses() {
        return responses;
    }

    /**
     * Returns the errors encountered during the batch processing, or an empty list if there were none.
     */
    public List<BatchError> errors() {
        return errors;
    }

    /**
     * Returns {@code true} if the batch is still processing (not in a terminal state).
     */
    public boolean isInProgress() {
        return !state.isTerminal();
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

    /**
     * Returns a new builder for constructing {@link BatchResponse} instances.
     *
     * @param <T> the type of the responses payload
     * @return a new builder instance
     */
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    /**
     * Builder for constructing {@link BatchResponse} instances.
     *
     * @param <T> the type of the responses payload
     */
    public static class Builder<T> {

        private String batchId;
        private BatchState state;
        private List<T> responses;
        private List<BatchError> errors;

        /**
         * Sets the unique identifier of the batch operation.
         */
        public Builder<T> batchId(String batchId) {
            this.batchId = batchId;
            return this;
        }

        /**
         * Sets the current state of the batch job.
         */
        public Builder<T> state(BatchState state) {
            this.state = state;
            return this;
        }

        /**
         * Sets the successful responses of the batch operation.
         */
        public Builder<T> responses(List<T> responses) {
            this.responses = responses;
            return this;
        }

        /**
         * Sets the errors encountered during the batch operation.
         */
        public Builder<T> errors(List<BatchError> errors) {
            this.errors = errors;
            return this;
        }

        /**
         * Builds a new {@link BatchResponse} instance.
         */
        public BatchResponse<T> build() {
            return new BatchResponse<>(this);
        }
    }
}
