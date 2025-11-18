package dev.langchain4j.model.googleai;

import static dev.langchain4j.internal.Utils.getOrDefault;

import java.util.List;
import java.util.Map;
import dev.langchain4j.Experimental;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchGenerateContentRequest;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchGenerateContentRequest.Batch;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchGenerateContentRequest.InlinedRequest;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchGenerateContentRequest.InputConfig;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchGenerateContentRequest.Requests;
import dev.langchain4j.model.googleai.BatchRequestResponse.Operation;
import org.jspecify.annotations.Nullable;

/**
 * Abstract base class for Google AI Gemini batch processing.
 * Provides common functionality for batch operations including creation, retrieval, cancellation, and deletion.
 */
@Experimental
abstract class AbstractGeminiBatchClient<REQUEST, RESPONSE> {

    protected final GeminiService geminiService;

    protected AbstractGeminiBatchClient(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    /**
     * Represents the response of a batch operation.
     */
    public sealed interface BatchResponse permits BatchIncomplete, BatchSuccess, BatchError {
    }

    /**
     * Represents a batch operation that is currently pending or in progress.
     */
    public record BatchIncomplete(BatchName batchName, BatchJobState state) implements BatchResponse {
    }

    /**
     * Represents a successful batch operation.
     */
    public record BatchSuccess<T>(BatchName batchName, List<T> responses) implements BatchResponse {
    }

    /**
     * Represents an error that occurred during a batch operation.
     */
    public record BatchError(int code, String message, BatchJobState state, List<Map<String, Object>> details)
            implements BatchResponse {
    }

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
    protected BatchResponse createBatchInline(
            String displayName, @Nullable Long priority, List<REQUEST> requests, String modelName) {

        var inlineRequests = requests.stream()
                .map(this::prepareRequest)
                .map(this::createInlinedRequest)
                .map(request -> new InlinedRequest(request, Map.of()))
                .toList();

        var request = new BatchGenerateContentRequest(
                new Batch(displayName, new InputConfig(new Requests(inlineRequests)), getOrDefault(priority, 0L)));

        return processResponse(geminiService.batchGenerateContent(modelName, request));
    }

    /**
     * Retrieves the current state and results of a batch operation.
     */
    public BatchResponse retrieveBatchResults(BatchName name) {
        var operation = geminiService.batchRetrieveBatch(name.value());
        return processResponse(operation);
    }

    /**
     * Cancels a batch operation that is currently pending or running.
     */
    public void cancelBatchJob(BatchName name) {
        geminiService.batchCancelBatch(name.value());
    }

    /**
     * Deletes a batch job.
     */
    public void deleteBatchJob(BatchName name) {
        // TODO(issues/3916): Implement deletion of batch jobs.
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * Lists batch jobs.
     */
    public void listBatchJobs() {
        // TODO(issues/3916): Implement listing of batch jobs.
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * Prepares a request for batch processing (e.g., applies default parameters).
     */
    protected abstract REQUEST prepareRequest(REQUEST request);

    /**
     * Creates an inlined request suitable for batching.
     */
    protected abstract REQUEST createInlinedRequest(REQUEST request);

    /**
     * Extracts responses from the batch operation result.
     */
    protected abstract List<RESPONSE> extractResponses(BatchRequestResponse.BatchGenerateContentResponse.Response response);

    /**
     * Processes the operation response and returns the appropriate BatchResponse.
     */
    protected BatchResponse processResponse(Operation operation) {
        if (operation.done()) {
            if (operation.error() != null) {
                var state = extractBatchState(operation.metadata());
                return new BatchError(
                        operation.error().code(),
                        operation.error().message(),
                        state,
                        operation.error().details());
            } else {
                List<RESPONSE> responses = extractResponses(operation.response());
                return new BatchSuccess<>(new BatchName(operation.name()), responses);
            }
        } else {
            var state = extractBatchState(operation.metadata());
            return new BatchIncomplete(new BatchName(operation.name()), state);
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
}
