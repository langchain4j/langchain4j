package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.model.googleai.BatchRequestResponse.Operation.Status;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class BatchRequestResponse {
    private BatchRequestResponse() {}

    /**
     * Represents the response of a batch operation.
     */
    public sealed interface BatchResponse<T> permits BatchIncomplete, BatchSuccess, BatchError {}

    /**
     * Represents a batch operation that is currently pending or in progress.
     */
    public record BatchIncomplete<T>(BatchName batchName, BatchJobState state) implements BatchResponse<T> {}

    /**
     * Represents a successful batch operation.
     */
    public record BatchSuccess<T>(BatchName batchName, List<T> responses, @Nullable List<Operation.Status> errors)
            implements BatchResponse<T> {}

    /**
     * Represents an error that occurred during a batch operation.
     */
    public record BatchError<T>(
            BatchName batchName, int code, String message, BatchJobState state, List<Map<String, Object>> details)
            implements BatchResponse<T> {}

    /**
     * Represents a List of Batches.
     *
     * @param pageToken Token used to paginate to the next page.
     * @param responses List of batch responses.
     */
    public record BatchList<T>(String pageToken, List<BatchResponse<T>> responses) {}

    /**
     * Represents the name of a batch operation.
     */
    public record BatchName(String value) {
        public BatchName {
            ensureOperationNameFormat(value);
        }

        private static void ensureOperationNameFormat(String operationName) {
            if (!operationName.startsWith("batches/")) {
                throw new IllegalArgumentException(
                        "Batch name must start with 'batches/'. This name is returned when creating "
                                + "the batch with #createBatchInline.");
            }
        }
    }

    /**
     * Represents the possible states of a batch job.
     */
    public enum BatchJobState {
        BATCH_STATE_PENDING,
        BATCH_STATE_RUNNING,
        BATCH_STATE_SUCCEEDED,
        BATCH_STATE_FAILED,
        BATCH_STATE_CANCELLED,
        BATCH_STATE_EXPIRED,
        UNSPECIFIED
    }

    /**
     * Represents a batch request for the Gemini API.
     * A batch allows processing multiple requests asynchronously.
     *
     * @param <REQ> The type of request (e.g., GeminiGenerateContentRequest, GeminiEmbeddingRequest)
     */
    record BatchCreateRequest<REQ>(Batch<REQ> batch) {

        /**
         * The batch configuration containing display name, input config, and priority.
         *
         * @param displayName Required. The user-defined name of this batch.
         * @param inputConfig Configuration for the input to the batch request.
         * @param priority    Optional. The priority of the batch. Batches with a higher priority value will be processed before
         *                    batches with a lower priority value. Negative values are allowed. Default is 0.
         */
        record Batch<REQ>(
                @JsonProperty("display_name") String displayName,
                @JsonProperty("input_config") InputConfig<REQ> inputConfig,
                long priority) {}

        /**
         * Configures the input to the batch request.
         *
         * @param requests The list of inlined requests to be processed in the batch.
         */
        record InputConfig<REQ>(Requests<REQ> requests) {}

        /**
         * Wrapper for the list of inlined requests.
         *
         * @param requests The list of inlined requests to be processed in the batch.
         */
        record Requests<REQ>(List<InlinedRequest<REQ>> requests) {}

        /**
         * Individual request to be processed in the batch.
         *
         * @param request  Required. The request to be processed in the batch.
         * @param metadata Optional. The metadata to be associated with the request.
         */
        record InlinedRequest<REQ>(REQ request, Map<String, String> metadata) {}
    }

    record BatchCreateFileRequest(FileBatch batch) {

        record FileBatch(
                @JsonProperty("display_name") String displayName,
                @JsonProperty("input_config") FileInputConfig inputConfig) {}

        record FileInputConfig(@JsonProperty("file_name") String fileName) {}
    }

    /**
     * Represents the response from a batch operation.
     *
     * @param <RESP> The type of response (e.g., GeminiGenerateContentResponse, GeminiEmbeddingResponse)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record BatchCreateResponse<RESP>(@JsonProperty("@type") String type, InlinedResponses<RESP> inlinedResponses) {

        /**
         * Wrapper for the list of inlined responses.
         *
         * @param inlinedResponses The list of individual response wrappers.
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        record InlinedResponses<RESP>(List<InlinedResponseWrapper<RESP>> inlinedResponses) {}

        /**
         * Wrapper for an individual (successful) response OR error.
         *
         * @param response A successful Gemini response.
         * @param error An error including message and code
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        record InlinedResponseWrapper<RESP>(@Nullable RESP response, @Nullable Status error) {}
    }

    /**
     * Represents a long-running operation that is the result of a network API call.
     *
     * @param <RESP> The type of response in the operation result
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Operation<RESP>(
            String name, Map<String, Object> metadata, boolean done, Status error, BatchCreateResponse<RESP> response) {

        /**
         * Represents the error status of an operation.
         *
         * @param code    The status code.
         * @param message A developer-facing error message.
         * @param details A list of messages that carry the error details.
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Status(int code, String message, @Nullable List<Map<String, Object>> details) {}
    }

    /**
     * Represents a response containing a list of operations and a token for pagination.
     *
     * @param <RESP>        the type of the response for each operation
     * @param operations    a list of operations to be performed
     * @param nextPageToken a token for retrieving the next page of operations, if available; null if there are no more pages
     */
    record ListOperationsResponse<RESP>(@Nullable List<Operation<RESP>> operations, @Nullable String nextPageToken) {}

    /**
     * Represents a batch request for a file operation.
     *
     * @param <REQ>   the type of the request payload
     * @param key     a unique identifier for the request
     * @param request the actual request payload containing the details of the operation
     */
    public record BatchFileRequest<REQ>(String key, REQ request) {}
}
