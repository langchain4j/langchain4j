package dev.langchain4j.model.anthropic;

import java.util.List;

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
}
