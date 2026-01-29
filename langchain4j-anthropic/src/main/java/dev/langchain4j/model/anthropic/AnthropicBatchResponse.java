package dev.langchain4j.model.anthropic;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import java.util.List;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Response from a batch operation, which can be in progress, completed successfully, or completed with errors.
 *
 * @param <T> the type of individual results
 */
public sealed interface AnthropicBatchResponse<T>
        permits AnthropicBatchResponse.AnthropicBatchIncomplete,
                AnthropicBatchResponse.AnthropicBatchSuccess,
                AnthropicBatchResponse.AnthropicBatchError {

    /**
     * @return the batch name/identifier
     */
    AnthropicBatchName name();

    /**
     * Represents a batch that is still being processed.
     *
     * @param name             the batch identifier
     * @param processingStatus the current status ("in_progress" or "canceling")
     * @param processingCount  number of requests still processing
     * @param succeededCount   number of requests that have succeeded so far
     * @param erroredCount     number of requests that have errored so far
     * @param canceledCount    number of requests that have been canceled
     * @param expiredCount     number of requests that have expired
     * @param createdAt        ISO 8601 timestamp when the batch was created
     * @param expiresAt        ISO 8601 timestamp when the batch will expire
     * @param <T>              the type of individual results
     */
    record AnthropicBatchIncomplete<T>(
            AnthropicBatchName name,
            String processingStatus,
            int processingCount,
            int succeededCount,
            int erroredCount,
            int canceledCount,
            int expiredCount,
            String createdAt,
            String expiresAt)
            implements AnthropicBatchResponse<T> {}

    /**
     * Represents a successfully completed batch with results.
     *
     * @param name           the batch identifier
     * @param results        list of individual results
     * @param succeededCount number of successful requests
     * @param erroredCount   number of errored requests
     * @param canceledCount  number of canceled requests
     * @param expiredCount   number of expired requests
     * @param createdAt      ISO 8601 timestamp when the batch was created
     * @param endedAt        ISO 8601 timestamp when processing ended
     * @param <T>            the type of individual results
     */
    record AnthropicBatchSuccess<T>(
            AnthropicBatchName name,
            List<AnthropicBatchIndividualResult<T>> results,
            int succeededCount,
            int erroredCount,
            int canceledCount,
            int expiredCount,
            String createdAt,
            String endedAt)
            implements AnthropicBatchResponse<T> {}

    /**
     * Represents a batch that ended with all requests failing.
     *
     * @param name         the batch identifier
     * @param errorMessage description of the error
     * @param createdAt    ISO 8601 timestamp when the batch was created
     * @param endedAt      ISO 8601 timestamp when processing ended
     * @param <T>          the type of individual results
     */
    record AnthropicBatchError<T>(AnthropicBatchName name, String errorMessage, String createdAt, String endedAt)
            implements AnthropicBatchResponse<T> {}

    /**
     * Represents an Anthropic Message Batch identifier.
     *
     * <p>Batch IDs follow the format {@code msgbatch_XXXXX} where XXXXX is a unique identifier.</p>
     *
     * @param id the batch identifier string
     */
    record AnthropicBatchName(String id) {

        public AnthropicBatchName {
            ensureNotBlank(id, "id");
            if (!id.startsWith("msgbatch_")) {
                throw new IllegalArgumentException(
                        "Invalid batch ID format. Expected format: msgbatch_XXXXX, got: " + id);
            }
        }

        /**
         * Creates a new batch name from a string ID.
         *
         * @param id the batch ID string
         * @return a new {@link AnthropicBatchName}
         */
        public static AnthropicBatchName of(String id) {
            return new AnthropicBatchName(id);
        }

        @Override
        @NonNull
        public String toString() {
            return id;
        }
    }

    /**
     * Individual result within a batch response.
     *
     * @param customId the custom ID provided when creating the request
     * @param result the successful result, or null if the request failed
     * @param error error message if the request failed, or null if successful
     * @param resultType the type of result: "succeeded", "errored", "canceled", or "expired"
     * @param <T> the type of the result
     */
    record AnthropicBatchIndividualResult<T>(
            String customId, @Nullable T result, @Nullable String error, String resultType) {

        /**
         * @return true if this request succeeded
         */
        public boolean isSucceeded() {
            return "succeeded".equals(resultType);
        }

        /**
         * @return true if this request errored
         */
        public boolean isErrored() {
            return "errored".equals(resultType);
        }

        /**
         * @return true if this request was canceled
         */
        public boolean isCanceled() {
            return "canceled".equals(resultType);
        }

        /**
         * @return true if this request expired
         */
        public boolean isExpired() {
            return "expired".equals(resultType);
        }

        public static <T> Builder<T> builder() {
            return new Builder<>();
        }

        public static class Builder<T> {
            private String customId;
            private T result;
            private String error;
            private String resultType;

            public Builder<T> customId(String customId) {
                this.customId = customId;
                return this;
            }

            public Builder<T> result(T result) {
                this.result = result;
                return this;
            }

            public Builder<T> error(String error) {
                this.error = error;
                return this;
            }

            public Builder<T> resultType(String resultType) {
                this.resultType = resultType;
                return this;
            }

            public AnthropicBatchIndividualResult<T> build() {
                return new AnthropicBatchIndividualResult<>(customId, result, error, resultType);
            }
        }
    }
}
