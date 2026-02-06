package dev.langchain4j.model.googleai;

import static dev.langchain4j.internal.Utils.firstNotNull;
import static dev.langchain4j.internal.Utils.getOrDefault;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchCreateFileRequest;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchCreateFileRequest.FileBatch;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchCreateFileRequest.FileInputConfig;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchCreateRequest;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchCreateRequest.Batch;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchCreateRequest.InlinedRequest;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchCreateRequest.InputConfig;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchCreateRequest.Requests;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchCreateResponse;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchJobState;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchList;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchName;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchResponse;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchSuccess;
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
    BatchResponse<RESPONSE> createBatchInline(
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

        return processResponse(geminiService.batchCreate(modelName, request, operationType), preparer);
    }

    BatchResponse<RESPONSE> createBatchFromFile(
            String displayName,
            GeminiFiles.GeminiFile file,
            String modelName,
            GeminiService.BatchOperationType operationType) {
        return processResponse(
                geminiService.batchCreate(
                        modelName,
                        new BatchCreateFileRequest(new FileBatch(displayName, new FileInputConfig(file.name()))),
                        operationType),
                preparer);
    }

    /**
     * Retrieves the current state and results of a batch operation.
     */
    @SuppressWarnings("unchecked")
    BatchResponse<RESPONSE> retrieveBatchResults(BatchName name) {
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
        geminiService.batchDeleteBatch(name.value());
    }

    /**
     * Lists batch jobs.
     */
    @SuppressWarnings("unchecked")
    BatchList<RESPONSE> listBatchJobs(@Nullable Integer pageSize, @Nullable String pageToken) {
        var response = geminiService.<List<API_RESPONSE>>batchListBatches(pageSize, pageToken);

        return new BatchList<>(
                response.nextPageToken(),
                firstNotNull("operationsResponse", response.operations(), List.of()).stream()
                        .map(operation -> processResponse((Operation<API_RESPONSE>) operation, preparer))
                        .toList());
    }

    /**
     * Processes the operation response and returns the appropriate BatchResponse.
     */
    private BatchResponse<RESPONSE> processResponse(
            Operation<API_RESPONSE> operation, RequestPreparer<REQUEST, API_REQUEST, API_RESPONSE, RESPONSE> preparer) {
        if (operation.done()) {
            if (operation.error() != null) {
                return new BatchRequestResponse.BatchError<>(
                        new BatchName(operation.name()),
                        operation.error().code(),
                        operation.error().message(),
                        extractBatchState(operation.metadata()),
                        operation.error().details());
            } else {
                var extractedResults = preparer.extractResults(operation.response());
                return new BatchSuccess<>(
                        new BatchName(operation.name()), extractedResults.responses(), extractedResults.errors());
            }
        } else {
            return new BatchRequestResponse.BatchIncomplete<>(
                    new BatchName(operation.name()), extractBatchState(operation.metadata()));
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

    record ExtractedBatchResults<T>(List<T> responses, List<BatchRequestResponse.Operation.Status> errors) {}

    /**
     * Interface for preparing requests and extracting responses.
     */
    interface RequestPreparer<REQUEST, API_REQUEST, API_RESPONSE, RESPONSE> {
        REQUEST prepareRequest(REQUEST request);

        API_REQUEST createInlinedRequest(REQUEST request);

        ExtractedBatchResults<RESPONSE> extractResults(BatchCreateResponse<API_RESPONSE> response);
    }
}
