package dev.langchain4j.model.batch;

import dev.langchain4j.Experimental;

import java.util.List;
import java.util.Objects;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Represents the responses of a batch operation.
 *
 * <p>A batch response contains the batch identifier, current state, and the per-request
 * {@link #results() results} once the batch has reached a terminal state.</p>
 *
 * <p>The {@link #results()} preserve the order of the submitted requests, so the i-th result
 * corresponds to the i-th request, allowing the caller to correlate every outcome (success or
 * failure) with its originating request. The {@link #responses()} and {@link #errors()} methods
 * are convenience views over those results.</p>
 *
 * @param <T> the type of the responses payload (e.g., {@code ChatResponse}, {@code Embedding})
 */
@Experimental
public class BatchResponse<T> {

    private final String batchId;
    private final BatchState state;
    private final List<BatchItemResult<T>> results;

    public BatchResponse(Builder<T> builder) {
        this.batchId = ensureNotBlank(builder.batchId, "batchId");
        this.state = ensureNotNull(builder.state, "state");
        this.results = copy(builder.results);
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
     * Returns the per-request results in submission order, or an empty list if the batch has not
     * produced any results yet (e.g., it is still in progress).
     *
     * <p>The i-th element corresponds to the i-th submitted request. Each result is either a
     * {@link BatchItemResult.Success} or a {@link BatchItemResult.Failure}. A batch-level failure
     * (the whole operation failing) is represented as a single {@link BatchItemResult.Failure}.</p>
     */
    public List<BatchItemResult<T>> results() {
        return results;
    }

    /**
     * Convenience view returning only the successful responses, in submission order.
     *
     * <p>To correlate responses with their originating requests, use {@link #results()} instead.</p>
     */
    public List<T> responses() {
        return results.stream()
                .filter(BatchItemResult::isSuccess)
                .map(BatchItemResult::response)
                .toList();
    }

    /**
     * Convenience view returning only the errors, in submission order.
     *
     * <p>To correlate errors with their originating requests, use {@link #results()} instead.</p>
     */
    public List<BatchError> errors() {
        return results.stream()
                .filter(result -> !result.isSuccess())
                .map(BatchItemResult::error)
                .toList();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BatchResponse<?> that = (BatchResponse<?>) o;
        return Objects.equals(batchId, that.batchId) && state == that.state && Objects.equals(results, that.results);
    }

    @Override
    public int hashCode() {
        return Objects.hash(batchId, state, results);
    }

    @Override
    public String toString() {
        return "BatchResponse{" + "batchId=" + batchId + ", state=" + state + ", results=" + results + '}';
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
        private List<BatchItemResult<T>> results;

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
         * Sets the per-request results of the batch operation, in submission order.
         */
        public Builder<T> results(List<BatchItemResult<T>> results) {
            this.results = results;
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
