package dev.langchain4j.model.googleai;

import static dev.langchain4j.internal.Utils.firstNotNull;
import static dev.langchain4j.internal.Utils.getOrDefault;

import com.fasterxml.jackson.core.type.TypeReference;
import dev.langchain4j.Experimental;
import dev.langchain4j.model.batch.BatchJobState;
import dev.langchain4j.model.batch.BatchList;
import dev.langchain4j.model.batch.BatchName;
import dev.langchain4j.model.batch.BatchResponse;
import dev.langchain4j.model.batch.ExtractedBatchResults;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles batch processing operations for Google AI Gemini.
 * Uses composition to provide batch functionality to different model types.
 *
 * @param <REQUEST>      The high-level request type (e.g., ChatRequest, TextSegment)
 * @param <RESPONSE>     The high-level responses type (e.g., ChatResponse, Embedding)
 * @param <API_REQUEST>  The low-level API request type (e.g., GeminiGenerateContentRequest, GeminiEmbeddingRequest)
 * @param <API_RESPONSE> The low-level API responses type (e.g., GeminiGenerateContentResponse, GeminiEmbeddingResponse)
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
            @Nullable String displayName,
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

    /**
     * Creates a batch from a previously uploaded file.
     */
    BatchResponse<RESPONSE> createBatchFromFile(
            String displayName,
            GeminiFiles.GeminiFile file,
            String modelName,
            GeminiService.BatchOperationType operationType) {
        return processResponse(
                geminiService.batchCreate(
                        modelName,
                        new BatchCreateFileRequest(new FileBatch(displayName, new FileInputConfig(file.name()))),
                        operationType));
    }

    /**
     * Retrieves the current state and results of a batch operation.
     */
    BatchResponse<RESPONSE> retrieveBatchResults(BatchName name) {
        return processResponse(geminiService.batchRetrieveBatch(name.value()));
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
     * Lists batch jobs with optional pagination.
     */
    BatchList<RESPONSE> listBatchJobs(@Nullable Integer pageSize, @Nullable String pageToken) {
        var response = geminiService.<List<API_RESPONSE>>batchListBatches(pageSize, pageToken);

        var batches = firstNotNull("operationsResponse", response.operations(), List.<Operation>of())
                .stream()
                .map(this::processResponse)
                .toList();

        return new BatchList<>(batches, response.nextPageToken());
    }

    /**
     * Processes the operation responses and returns the appropriate BatchResponse.
     */
    private BatchResponse<RESPONSE> processResponse(Operation operation) {
        var state = extractBatchState(operation.metadata());
        var batchName = new BatchName(operation.name());

        if (operation.done()) {
            if (operation.error() != null) {
                return new BatchResponse<>(batchName, BatchJobState.BATCH_STATE_FAILED, List.of(), null);
            } else {
                BatchCreateResponse<API_RESPONSE> typedResponse = null;
                if (operation.response() != null) {
                    typedResponse = Json.convertValue(operation.response(), preparer.getResponseTypeReference());
                }
                var responses = preparer.extractResults(typedResponse);
                return new BatchResponse<>(batchName, BatchJobState.BATCH_STATE_SUCCEEDED, responses.responses(), responses.errors());
            }
        } else {
            return new BatchResponse<>(batchName, state, List.of(), null);
        }
    }

    private BatchJobState extractBatchState(@Nullable Map<String, Object> metadata) {
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
     *
     * @param <REQUEST>      the high-level request type
     * @param <API_REQUEST>  the low-level API request type
     * @param <API_RESPONSE> the low-level API responses type
     * @param <RESPONSE>     the high-level responses type
     */
    interface RequestPreparer<REQUEST, API_REQUEST, API_RESPONSE, RESPONSE> {
        /**
         * Prepares a request by applying defaults and overrides.
         */
        REQUEST prepareRequest(REQUEST request);

        /**
         * Converts a high-level request to the low-level API request format.
         */
        API_REQUEST createInlinedRequest(REQUEST request);

        /**
         * Extracts high-level responses from the API responses.
         */
        ExtractedBatchResults<RESPONSE> extractResults(@Nullable  BatchCreateResponse<API_RESPONSE>response);

        /**
         * Used to convert Json `Object` to `API_RESPONSE` types using Jackson.
         *
         * @see Json#convertValue(Object, TypeReference)
         */
        TypeReference<BatchCreateResponse<API_RESPONSE>> getResponseTypeReference();  // NEW
    }
}
