package dev.langchain4j.model.googleai;

import static dev.langchain4j.internal.Utils.firstNotNull;
import static dev.langchain4j.internal.Utils.getOrDefault;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.batch.BatchError;
import dev.langchain4j.model.batch.BatchId;
import dev.langchain4j.model.batch.BatchPage;
import dev.langchain4j.model.batch.BatchResponse;
import dev.langchain4j.model.batch.BatchState;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchCreateFileRequest;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchCreateFileRequest.FileBatch;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchCreateFileRequest.FileInputConfig;
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
    private final RequestPreparer<REQUEST, API_REQUEST, API_RESPONSE, RESPONSE> preparer;

    GeminiBatchProcessor(
            GeminiService geminiService, RequestPreparer<REQUEST, API_REQUEST, API_RESPONSE, RESPONSE> preparer) {
        this.geminiService = geminiService;
        this.preparer = preparer;
    }

    /**
     * Creates and enqueues a batch of requests for asynchronous processing.
     */
    BatchResponse<RESPONSE> createBatch(
            String displayName,
            @Nullable Long priority,
            List<REQUEST> requests,
            String modelName,
            GeminiService.BatchOperationType operationType) {
        var inlineRequests = requests.stream()
                .map(preparer::prepareRequest)
                .map(preparer::createInlinedRequest)
                .map(request -> new InlinedRequest<>(request, Map.of()))
                .toList();

        var request = new BatchCreateRequest<>(new Batch<>(
                displayName, new InputConfig<>(new Requests<>(inlineRequests)), getOrDefault(priority, 0L)));

        return processResponse(geminiService.batchCreate(modelName, request, operationType));
    }

    BatchResponse<RESPONSE> createBatchFromFile(
            String displayName,
            GeminiFiles.GeminiFile file,
            String modelName,
            GeminiService.BatchOperationType operationType) {
        return processResponse(geminiService.batchCreate(
                modelName,
                new BatchCreateFileRequest(new FileBatch(displayName, new FileInputConfig(file.name()))),
                operationType));
    }

    /**
     * Retrieves the current state and results of a batch operation.
     */
    @SuppressWarnings("unchecked")
    BatchResponse<RESPONSE> retrieveBatchResults(BatchId name) {
        var operation = geminiService.batchRetrieveBatch(name.value());
        return processResponse((Operation<API_RESPONSE>) operation);
    }

    /**
     * Cancels a batch operation that is currently pending or running.
     */
    void cancelBatchJob(BatchId name) {
        geminiService.batchCancelBatch(name.value());
    }

    /**
     * Deletes a batch job.
     */
    void deleteBatchJob(BatchId name) {
        geminiService.batchDeleteBatch(name.value());
    }

    /**
     * Lists batch jobs.
     */
    @SuppressWarnings("unchecked")
    BatchPage<RESPONSE> listBatchJobs(@Nullable Integer pageSize, @Nullable String pageToken) {
        var response = geminiService.<List<API_RESPONSE>>batchListBatches(pageSize, pageToken);

        return new BatchPage<>(
                firstNotNull("operationsResponse", response.operations(), List.of()).stream()
                        .map(operation -> processResponse((Operation<API_RESPONSE>) operation))
                        .toList(),
                response.nextPageToken());
    }

    /**
     * Processes the operation response and returns the appropriate BatchResponse.
     */
    private BatchResponse<RESPONSE> processResponse(Operation<API_RESPONSE> operation) {
        var state = extractBatchState(operation.metadata());
        var batchId = new BatchId(operation.name());

        if (operation.done()) {
            var error = operation.error();
            if (operation.error() != null) {
                return new BatchResponse<>(batchId, BatchState.FAILED, List.of(), List.of(error.toGenericStatus()));
            } else {
                var responses = preparer.extractResults(operation.response());
                return new BatchResponse<>(batchId, BatchState.SUCCEEDED, responses.responses(), responses.errors());
            }
        } else {
            return new BatchResponse<>(batchId, state, List.of(), null);
        }
    }

    private BatchState extractBatchState(@Nullable Map<String, Object> metadata) {
        if (metadata == null) {
            return BatchState.UNSPECIFIED;
        }

        var stateObj = metadata.get("state");
        if (stateObj == null) {
            return BatchState.UNSPECIFIED;
        }

        try {
            String stateStr = stateObj.toString();
            if (stateStr.startsWith("BATCH_STATE_")) {
                stateStr = stateStr.substring("BATCH_STATE_".length());
            }
            return BatchState.valueOf(stateStr);
        } catch (IllegalArgumentException e) {
            return BatchState.UNSPECIFIED;
        }
    }

    /**
     * Interface for preparing requests and extracting responses.
     */
    interface RequestPreparer<REQUEST, API_REQUEST, API_RESPONSE, RESPONSE> {
        REQUEST prepareRequest(REQUEST request);

        API_REQUEST createInlinedRequest(REQUEST request);

        ExtractedBatchResults<RESPONSE> extractResults(BatchCreateResponse<API_RESPONSE> response);
    }

    /**
     * Contains the extracted results from a batch operation, separating successful responses from errors.
     *
     * <p>This record is used internally by batch processors to return both successful responses
     * and any errors that occurred during batch processing. Each batch request may succeed or fail
     * independently, so a single batch operation can produce both responses and errors.</p>
     *
     * @param <T>       the type of successful response (e.g., {@code ChatResponse}, {@code Embedding})
     * @param responses the list of successful responses from the batch operation
     * @param errors    the list of errors that occurred for individual requests in the batch
     */
    record ExtractedBatchResults<T>(List<T> responses, List<BatchError> errors) {}
}
