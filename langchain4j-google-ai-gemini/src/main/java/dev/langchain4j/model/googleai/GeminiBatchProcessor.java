package dev.langchain4j.model.googleai;

import static dev.langchain4j.internal.Utils.getOrDefault;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.batch.BatchItemResult;
import dev.langchain4j.model.batch.BatchPage;
import dev.langchain4j.model.batch.BatchPagination;
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
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchFileRequest;
import dev.langchain4j.model.googleai.BatchRequestResponse.ListOperationsResponse;
import dev.langchain4j.model.googleai.BatchRequestResponse.Operation;
import dev.langchain4j.model.googleai.jsonl.JsonLinesWriter;
import java.io.IOException;
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
     * Writes the given requests to a JSONL writer in the Gemini batch file format,
     * converting each high-level request to its inlined API representation.
     */
    void writeBatch(JsonLinesWriter writer, Iterable<BatchFileRequest<REQUEST>> requests) throws IOException {
        for (var request : requests) {
            var preparedRequest = preparer.prepareRequest(request.request());
            var inlinedRequest = preparer.createInlinedRequest(preparedRequest);
            writer.write(new BatchFileRequest<>(request.key(), inlinedRequest));
        }
    }

    /**
     * Retrieves the current state and results of a batch operation.
     */
    @SuppressWarnings("unchecked")
    BatchResponse<RESPONSE> retrieveBatchResults(String batchId) {
        var operation = geminiService.batchRetrieveBatch(batchId);
        return processResponse((Operation<API_RESPONSE>) operation);
    }

    /**
     * Cancels a batch operation that is currently pending or running.
     */
    void cancelBatchJob(String batchId) {
        geminiService.batchCancelBatch(batchId);
    }

    /**
     * Deletes a batch job.
     */
    void deleteBatchJob(String batchId) {
        geminiService.batchDeleteBatch(batchId);
    }

    /**
     * Lists batch jobs.
     */
    BatchPage<RESPONSE> listBatchJobs(final @Nullable BatchPagination batchPagination) {
        var pageSize = batchPagination != null ? batchPagination.pageSize() : null;
        var pageToken = batchPagination != null ? batchPagination.pageToken() : null;
        ListOperationsResponse<API_RESPONSE> response = geminiService.batchListBatches(pageSize, pageToken);

        List<Operation<API_RESPONSE>> operations = getOrDefault(response.operations(), List.of());
        return new BatchPage<>(operations.stream().map(this::processResponse).toList(), response.nextPageToken());
    }

    /**
     * Processes the operation response and returns the appropriate BatchResponse.
     */
    private BatchResponse<RESPONSE> processResponse(Operation<API_RESPONSE> operation) {
        var state = extractBatchState(operation.metadata());
        var batchId = operation.name();

        if (operation.done()) {
            var error = operation.error();
            if (error != null) {
                // Batch-level failure: there is no per-request breakdown, so it is surfaced as a
                // single failed result.
                return BatchResponse.<RESPONSE>builder()
                        .batchId(batchId)
                        .state(BatchState.FAILED)
                        .results(List.of(BatchItemResult.failure(error.toGenericStatus())))
                        .build();
            }
            var results = preparer.extractResults(operation.response());
            // A done operation is SUCCEEDED unless the metadata reports another terminal state
            // (e.g. CANCELLED or EXPIRED), which must be preserved rather than reported as success.
            var finalState = state.isTerminal() ? state : BatchState.SUCCEEDED;
            return BatchResponse.<RESPONSE>builder()
                    .batchId(batchId)
                    .state(finalState)
                    .results(results)
                    .build();
        } else {
            return BatchResponse.<RESPONSE>builder()
                    .batchId(batchId)
                    .state(state)
                    .build();
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

        /**
         * Extracts the per-request results from a batch operation response, preserving the order of
         * the submitted requests so that each result can be correlated with its originating request.
         */
        List<BatchItemResult<RESPONSE>> extractResults(BatchCreateResponse<API_RESPONSE> response);
    }
}
