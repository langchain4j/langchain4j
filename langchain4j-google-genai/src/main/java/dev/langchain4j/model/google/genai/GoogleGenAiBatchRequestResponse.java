package dev.langchain4j.model.google.genai;

import dev.langchain4j.Experimental;
import java.util.List;
import java.util.Map;

/**
 * Common request and response classes for Gemini Batch Operations.
 */
@Experimental
public final class GoogleGenAiBatchRequestResponse {

    private GoogleGenAiBatchRequestResponse() {}

    /**
     * A record used to represent a single request within a batch job when using the File API.
     * The `key` serves as an identifier for the specific request, and `request` holds the data for the API call.
     */
    public record BatchFileRequest<T>(String key, T request) {}

    /**
     * Wrapper for a Batch Job Name.
     */
    public record BatchName(String value) {}

    /**
     * Enum mapping to the status of a Batch Job.
     */
    public enum BatchJobState {
        JOB_STATE_UNSPECIFIED,
        JOB_STATE_PENDING,
        JOB_STATE_RUNNING,
        JOB_STATE_SUCCEEDED,
        JOB_STATE_FAILED,
        JOB_STATE_CANCELLING,
        JOB_STATE_CANCELLED,
        JOB_STATE_EXPIRED,
        JOB_STATE_PARTIALLY_SUCCEEDED,
        UNRECOGNIZED
    }

    /**
     * The base response interface for batch operations.
     */
    public sealed interface BatchResponse<T> permits BatchSuccess, BatchIncomplete, BatchError {
        BatchName name();

        BatchJobState state();
    }

    /**
     * Represents a successfully completed batch operation.
     */
    public record BatchSuccess<T>(BatchName name, List<T> responses, List<Status> errors) implements BatchResponse<T> {
        @Override
        public BatchJobState state() {
            return BatchJobState.JOB_STATE_SUCCEEDED;
        }
    }

    /**
     * Represents a batch operation that is still in progress.
     */
    public record BatchIncomplete<T>(BatchName name, BatchJobState state) implements BatchResponse<T> {}

    /**
     * Represents a batch operation that failed entirely.
     */
    public record BatchError<T>(
            BatchName name, Integer code, String message, BatchJobState state, List<Map<String, Object>> details)
            implements BatchResponse<T> {}

    /**
     * Represents a paginated list of batch jobs.
     */
    public record BatchList<T>(String pageToken, List<BatchResponse<T>> batches) {}

    /**
     * A simple representation of an error status within an operation.
     */
    public record Status(Integer code, String message, List<Map<String, Object>> details) {}
}
