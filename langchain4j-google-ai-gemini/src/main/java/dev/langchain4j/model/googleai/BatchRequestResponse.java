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
     *
     * @param <T> the type of the response payload
     */
    public sealed interface BatchResponse<T> permits BatchIncomplete, BatchSuccess, BatchError {}

    /**
     * Represents a batch operation that is currently pending or in progress.
     *
     * @param <T>       the type of the response payload
     * @param batchName the name identifying the batch operation
     * @param state     the current state of the batch job
     */
    public record BatchIncomplete<T>(
            @JsonProperty("batchName") BatchName batchName,
            @JsonProperty("state") BatchJobState state) implements BatchResponse<T> {}

    /**
     * Represents a successful batch operation.
     *
     * @param <T>       the type of the response payload
     * @param batchName the name identifying the batch operation
     * @param responses the list of successful responses
     * @param errors    the list of errors that occurred during processing, or {@code null} if none
     */
    public record BatchSuccess<T>(
            @JsonProperty("batchName") BatchName batchName,
            @JsonProperty("responses") List<T> responses,
            @JsonProperty("errors") @Nullable List<Operation.Status> errors)
            implements BatchResponse<T> {}

    /**
     * Represents an error that occurred during a batch operation.
     *
     * @param <T>       the type of the response payload
     * @param batchName the name identifying the batch operation
     * @param code      the error status code
     * @param message   a developer-facing error message
     * @param state     the state of the batch job when the error occurred
     * @param details   a list of messages that carry the error details
     */
    public record BatchError<T>(
            @JsonProperty("batchName") BatchName batchName,
            @JsonProperty("code") int code,
            @JsonProperty("message") String message,
            @JsonProperty("state") BatchJobState state,
            @JsonProperty("details") List<Map<String, Object>> details)
            implements BatchResponse<T> {}

    /**
     * Represents the name of a batch operation.
     *
     * @param value the batch name string, must start with {@code "batches/"}
     */
    public record BatchName(@JsonProperty("value") String value) {
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
     * @param <REQ> the type of request (e.g., GeminiGenerateContentRequest, GeminiEmbeddingRequest)
     * @param batch the batch configuration
     */
    record BatchCreateRequest<REQ>(@JsonProperty("batch") Batch<REQ> batch) {

        /**
         * The batch configuration containing display name, input config, and priority.
         *
         * @param <REQ>       the type of request
         * @param displayName required, the user-defined name of this batch
         * @param inputConfig configuration for the input to the batch request
         * @param priority    optional, the priority of the batch. Batches with a higher priority value will be processed before
         *                    batches with a lower priority value. Negative values are allowed. Default is 0.
         */
        record Batch<REQ>(
                @JsonProperty("display_name") String displayName,
                @JsonProperty("input_config") InputConfig<REQ> inputConfig,
                @JsonProperty("priority") long priority) {}

        /**
         * Configures the input to the batch request.
         *
         * @param <REQ>    the type of request
         * @param requests the list of inlined requests to be processed in the batch
         */
        record InputConfig<REQ>(@JsonProperty("requests") Requests<REQ> requests) {}

        /**
         * Wrapper for the list of inlined requests.
         *
         * @param <REQ>    the type of request
         * @param requests the list of inlined requests to be processed in the batch
         */
        record Requests<REQ>(@JsonProperty("requests") List<InlinedRequest<REQ>> requests) {}

        /**
         * Individual request to be processed in the batch.
         *
         * @param <REQ>    the type of request
         * @param request  required, the request to be processed in the batch
         * @param metadata optional, the metadata to be associated with the request
         */
        record InlinedRequest<REQ>(
                @JsonProperty("request") REQ request,
                @JsonProperty("metadata") Map<String, String> metadata) {}
    }

    /**
     * Represents a batch creation request backed by a file.
     *
     * @param batch the file-based batch configuration
     */
    record BatchCreateFileRequest(@JsonProperty("batch") FileBatch batch) {

        /**
         * File-based batch configuration.
         *
         * @param displayName the user-defined name of this batch
         * @param inputConfig configuration referencing the input file
         */
        record FileBatch(
                @JsonProperty("display_name") String displayName,
                @JsonProperty("input_config") FileInputConfig inputConfig) {}

        /**
         * Configuration referencing an input file for a batch operation.
         *
         * @param fileName the name of the input file
         */
        record FileInputConfig(@JsonProperty("file_name") String fileName) {}
    }

    /**
     * Represents the response from a batch operation.
     *
     * @param <RESP>           the type of response (e.g., GeminiGenerateContentResponse, GeminiEmbeddingResponse)
     * @param type             the type identifier of the response
     * @param inlinedResponses the wrapper containing the list of inlined responses
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record BatchCreateResponse<RESP>(
            @JsonProperty("@type") String type,
            @JsonProperty("inlinedResponses") InlinedResponses<RESP> inlinedResponses) {

        /**
         * Wrapper for the list of inlined responses.
         *
         * @param <RESP>           the type of response
         * @param inlinedResponses the list of individual response wrappers
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        record InlinedResponses<RESP>(
                @JsonProperty("inlinedResponses") List<InlinedResponseWrapper<RESP>> inlinedResponses) {}

        /**
         * Wrapper for an individual (successful) response OR error.
         *
         * @param <RESP>   the type of response
         * @param response a successful Gemini response, or {@code null} if an error occurred
         * @param error    an error including message and code, or {@code null} if successful
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        record InlinedResponseWrapper<RESP>(
                @JsonProperty("response") @Nullable RESP response,
                @JsonProperty("error") @Nullable Status error) {}
    }

    /**
     * Represents a long-running operation that is the result of a network API call.
     *
     * @param <RESP>   the type of response in the operation result
     * @param name     the server-assigned name of the operation
     * @param metadata service-specific metadata associated with the operation
     * @param done     whether the operation has completed
     * @param error    the error result of the operation, if any
     * @param response the normal response of the operation, if successful
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Operation<RESP>(
            @JsonProperty("name") String name,
            @JsonProperty("metadata") Map<String, Object> metadata,
            @JsonProperty("done") boolean done,
            @JsonProperty("error") Status error,
            @JsonProperty("response") BatchCreateResponse<RESP> response) {

        /**
         * Represents the error status of an operation.
         *
         * @param code    the status code
         * @param message a developer-facing error message
         * @param details a list of messages that carry the error details, or {@code null} if none
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Status(
                @JsonProperty("code") int code,
                @JsonProperty("message") String message,
                @JsonProperty("details") @Nullable List<Map<String, Object>> details) {
            public dev.langchain4j.model.batch.BatchError toGenericStatus() {
                return new dev.langchain4j.model.batch.BatchError(code, message, details);
            }
        }
    }

    /**
     * Represents a response containing a list of operations and a token for pagination.
     *
     * @param <RESP>        the type of the response for each operation
     * @param operations    a list of operations, or {@code null} if none
     * @param nextPageToken a token for retrieving the next page of operations, or {@code null} if there are no more pages
     */
    record ListOperationsResponse<RESP>(
            @JsonProperty("operations") @Nullable List<Operation<RESP>> operations,
            @JsonProperty("nextPageToken") @Nullable String nextPageToken) {}

    /**
     * Represents a batch request for a file operation.
     *
     * @param <REQ>   the type of the request payload
     * @param key     a unique identifier for the request
     * @param request the actual request payload containing the details of the operation
     */
    public record BatchFileRequest<REQ>(
            @JsonProperty("key") String key,
            @JsonProperty("request") REQ request) {}
}
