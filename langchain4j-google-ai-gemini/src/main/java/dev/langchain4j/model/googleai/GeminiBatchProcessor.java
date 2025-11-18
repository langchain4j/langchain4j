package dev.langchain4j.model.googleai;

import static dev.langchain4j.internal.Utils.getOrDefault;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchCreateRequest;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchCreateRequest.Batch;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchCreateRequest.InlinedRequest;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchCreateRequest.InputConfig;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchCreateRequest.Requests;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchCreateResponse;
import dev.langchain4j.model.googleai.BatchRequestResponse.Operation;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Handles batch processing operations for Google AI Gemini.
 * Uses composition to provide batch functionality to different model types.
 *
 * @param <REQUEST>      The high-level request type (e.g., ChatRequest, TextSegment)
 * @param <RESPONSE>     The high-level response type (e.g., ChatResponse, Embedding)
 * @param <API_REQUEST>  The low-level API request type (e.g., GeminiGenerateContentRequest, GeminiEmbeddingRequest)
 * @param <API_RESPONSE> The low-level API response type (e.g., GeminiGenerateContentResponse, GeminiEmbeddingResponse)
 */
@Experimental
final class GeminiBatchProcessor<REQUEST, RESPONSE, API_REQUEST, API_RESPONSE> {
    private final GeminiService geminiService;

    GeminiBatchProcessor(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

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
    public record BatchSuccess<T>(BatchName batchName, List<T> responses) implements BatchResponse<T> {}

    /**
     * Represents an error that occurred during a batch operation.
     */
    public record BatchError<T>(int code, String message, BatchJobState state, List<Map<String, Object>> details)
            implements BatchResponse<T> {}

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
     * Creates and enqueues a batch of requests for asynchronous processing.
     */
    BatchResponse<RESPONSE> createBatchInline(
            String displayName,
            @Nullable Long priority,
            List<REQUEST> requests,
            String modelName,
            RequestPreparer<REQUEST, API_REQUEST, API_RESPONSE, RESPONSE> preparer) {

        var inlineRequests = requests.stream()
                .map(preparer::prepareRequest)
                .map(preparer::createInlinedRequest)
                .map(request -> new InlinedRequest<>(request, Map.of()))
                .toList();

        var request = new BatchCreateRequest<>(new Batch<>(
                displayName, new InputConfig<>(new Requests<>(inlineRequests)), getOrDefault(priority, 0L)));

        return processResponse(geminiService.batchCreate(modelName, request), preparer);
    }

    /**
     * Retrieves the current state and results of a batch operation.
     */
    @SuppressWarnings("unchecked")
    BatchResponse<RESPONSE> retrieveBatchResults(
            BatchName name, RequestPreparer<REQUEST, API_REQUEST, API_RESPONSE, RESPONSE> preparer) {
        var operation = geminiService.batchRetrieveBatch(name.value());
        return processResponse((Operation<API_RESPONSE>) operation, preparer);
    }

    /**
     * Cancels a batch operation that is currently pending or running.
     */
    void cancelBatchJob(BatchName name) {
        geminiService.batchCancelBatch(name.value());
    }

    /**
     * Deletes a batch job.
     */
    void deleteBatchJob(BatchName name) {
        // TODO(issues/3916): Implement deletion of batch jobs.
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * Lists batch jobs.
     */
    void listBatchJobs() {
        // TODO(issues/3916): Implement listing of batch jobs.
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * Processes the operation response and returns the appropriate BatchResponse.
     */
    private BatchResponse<RESPONSE> processResponse(
            Operation<API_RESPONSE> operation, RequestPreparer<REQUEST, API_REQUEST, API_RESPONSE, RESPONSE> preparer) {
        if (operation.done()) {
            if (operation.error() != null) {
                return new BatchError<>(
                        operation.error().code(),
                        operation.error().message(),
                        extractBatchState(operation.metadata()),
                        operation.error().details());
            } else {
                return new BatchSuccess<>(
                        new BatchName(operation.name()), preparer.extractResponses(operation.response()));
            }
        } else {
            return new BatchIncomplete<>(new BatchName(operation.name()), extractBatchState(operation.metadata()));
        }
    }

    private BatchJobState extractBatchState(Map<String, Object> metadata) {
        if (metadata == null) {
            return BatchJobState.UNSPECIFIED;
        }

        var stateObj = metadata.get("state");
        if (stateObj == null) {
            return BatchJobState.UNSPECIFIED;
        }

        try {
            return BatchJobState.valueOf(stateObj.toString());
        } catch (IllegalArgumentException e) {
            return BatchJobState.UNSPECIFIED;
        }
    }

    /**
     * Interface for preparing requests and extracting responses.
     */
    interface RequestPreparer<REQUEST, API_REQUEST, API_RESPONSE, RESPONSE> {
        REQUEST prepareRequest(REQUEST request);

        API_REQUEST createInlinedRequest(REQUEST request);

        List<RESPONSE> extractResponses(BatchCreateResponse<API_RESPONSE> response);
    }
}
