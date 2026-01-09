package dev.langchain4j.model.anthropic.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * Response from creating or retrieving a Message Batch.
 *
 * @param id unique identifier for the batch (e.g., "msgbatch_01HkcTjaV5uDC8jWR4ZsDV8d")
 * @param type always "message_batch"
 * @param processingStatus current status: "in_progress", "canceling", or "ended"
 * @param requestCounts counts of requests in each state
 * @param endedAt ISO 8601 timestamp when processing ended, or null if still processing
 * @param createdAt ISO 8601 timestamp when the batch was created
 * @param expiresAt ISO 8601 timestamp when the batch will expire (24 hours from creation)
 * @param cancelInitiatedAt ISO 8601 timestamp when cancellation was requested, or null
 * @param resultsUrl URL to download results when processing has ended, or null
 */
@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public record AnthropicBatchResponse(
        String id,
        String type,
        String processingStatus,
        AnthropicBatchRequestCounts requestCounts,
        String endedAt,
        String createdAt,
        String expiresAt,
        String cancelInitiatedAt,
        String resultsUrl) {

    /**
     * @return true if the batch is still being processed
     */
    public boolean isInProgress() {
        return "in_progress".equals(processingStatus);
    }

    /**
     * @return true if the batch is being canceled
     */
    public boolean isCanceling() {
        return "canceling".equals(processingStatus);
    }

    /**
     * @return true if all requests have finished processing
     */
    public boolean isEnded() {
        return "ended".equals(processingStatus);
    }
}
