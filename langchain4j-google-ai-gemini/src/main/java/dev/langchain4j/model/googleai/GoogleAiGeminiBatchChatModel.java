package dev.langchain4j.model.googleai;

import static dev.langchain4j.internal.Utils.getOrDefault;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchGenerateContentRequest;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchGenerateContentRequest.Batch;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchGenerateContentRequest.InlinedRequest;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchGenerateContentRequest.InputConfig;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchGenerateContentRequest.Requests;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchGenerateContentResponse;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchGenerateContentResponse.Response;
import dev.langchain4j.model.googleai.BatchRequestResponse.Operation;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Provides an interface for interacting with the Gemini Batch API, an asynchronous service designed for processing
 * large volumes of requests at a reduced cost (50% of standard). It is ideal for non-urgent, large-scale tasks like
 * data pre-processing or evaluations, with a Service Level Objective (SLO) of 24-hour turnaround, though
 * completion is often much quicker.
 */
@Experimental
public final class GoogleAiGeminiBatchChatModel extends BaseGeminiChatModel {
    GoogleAiGeminiBatchChatModel(final Builder builder) {
        this(builder, buildGeminiService(builder));
    }

    GoogleAiGeminiBatchChatModel(final Builder builder, final GeminiService geminiService) {
        super(builder, geminiService);
    }

    /**
     * Represents the response of a batch operation.
     * This interface is sealed, allowing only specific implementations:
     * {@link BatchIncomplete}, {@link BatchSuccess}, and {@link BatchError}.
     */
    public sealed interface BatchResponse permits BatchIncomplete, BatchSuccess, BatchError {
    }

    /**
     * Represents a batch operation that is currently pending or in progress.
     *
     * @param batchName the name of the batch operation
     * @param state     the current state of the batch job
     */
    public record BatchIncomplete(BatchName batchName, BatchJobState state) implements BatchResponse {
    }

    /**
     * Represents a successful batch operation.
     *
     * @param batchName the name of the batch operation
     * @param responses a list of chat responses from the batch operation
     */
    public record BatchSuccess(BatchName batchName, List<ChatResponse> responses) implements BatchResponse {
    }

    /**
     * Represents an error that occurred during a batch operation.
     *
     * @param batchName the name of the batch operation
     * @param code      an error code representing the type of error
     * @param message   a descriptive message about the error
     * @param details   additional details about the error, if available
     */
    public record BatchError(BatchName batchName, int code, String message, BatchJobState state,
                             List<Map<String, Object>> details)
            implements BatchResponse {
    }

    /**
     * Represents a List of Batches, returned from {@link GoogleAiGeminiBatchChatModel#listBatchJobs(String, Integer, String)}
     *
     * @param pageToken Token used to paginate to the next page.
     * @param responses List of batch responses.
     */
    public record BatchList(String pageToken, List<BatchResponse> responses) {
    }

    /**
     * Represents the name of a batch operation.
     * The name must adhere to a specific format, starting with "batches/".
     *
     * @param value the name of the batch operation
     */
    public record BatchName(String value) {
        public BatchName {
            ensureOperationNameFormat(value);
        }

        /**
         * Ensures that the operation name starts with "batches/".
         *
         * @param operationName the name of the operation to validate
         * @throws IllegalArgumentException if the operation name does not start with "batches/"
         */
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
     * Creates and enqueues a batch of content generation requests for asynchronous processing.
     *
     * <p> This method submits multiple chat requests as a single batch operation to the Gemini API.
     * All requests in the batch must use the same model. The batch will be processed asynchronously,
     * and the initial response will typically be in a {@link BatchIncomplete} state.</p>
     *
     * <p>Batch processing offers a 50% cost reduction compared to real-time requests and has a
     * 24-hour turnaround SLO, making it ideal for large-scale, non-urgent tasks.</p>
     *
     * <p><strong>Note:</strong> The inline API allows for a total request size of 20MB or under. Larger requests
     * should use the File API</p>
     *
     * @param displayName a user-defined name for the batch, used for identification
     * @param priority    optional priority for the batch; batches with higher priority values are
     *                    processed before those with lower values; negative values are allowed;
     *                    defaults to 0 if null
     * @param requests    a list of chat requests to be processed in the batch; all requests must
     *                    use the same model
     * @return a {@link BatchResponse} representing the initial state of the batch operation,
     * typically {@link BatchIncomplete}
     * @throws IllegalArgumentException if the requests contain different models
     */
    public BatchResponse createBatchInline(String displayName, @Nullable Long priority, List<ChatRequest> requests) {
        var modelName = extractModelFromRequests(requests);
        var inlineRequests = requests.stream()
                // Merge the chat requests with the values set in the Builder.
                .map(this::applyDefaultParameters)
                // Create a Gemini specific content requests for each ChatRequest.
                .map(this::createGenerateContentRequest)
                // Wrap them in InlinedRequest, ready for batching.
                .map(request -> new InlinedRequest(request, Map.of()))
                .toList();
        var request = new BatchGenerateContentRequest(
                new Batch(displayName, new InputConfig(new Requests(inlineRequests)), getOrDefault(priority, 0L)));

        return processResponse(geminiService.batchGenerateContent(modelName, request));
    }

    /**
     * Retrieves the current state and results of a batch operation.
     *
     * <p>This method polls the Gemini API to get the latest state of a previously created batch.
     * The response can be:
     * <ul>
     *   <li>{@link BatchIncomplete} - if the batch is still pending or running</li>
     *   <li>{@link BatchSuccess} - if the batch completed successfully, containing all responses</li>
     *   <li>{@link BatchError} - if the batch failed, containing error details</li>
     * </ul>
     * <p>
     * Clients should poll this method at intervals to check the operation status until completion.</p>
     *
     * @param name the name of the batch operation to retrieve, obtained from the initial
     *             {@link #createBatchInline} call
     * @return a {@link BatchResponse} representing the current state of the batch operation
     */
    public BatchResponse retrieveBatchResults(BatchName name) {
        var operation = geminiService.batchRetrieveBatch(name.value());
        return processResponse(operation);
    }

    /**
     * Cancels a batch operation that is currently pending or running.
     *
     * <p>This method attempts to cancel a batch job. Cancellation is only possible for batches
     * that are in {@link BatchJobState#BATCH_STATE_PENDING} or {@link BatchJobState#BATCH_STATE_RUNNING}
     * state. Batches that have already completed, failed, or been cancelled cannot be cancelled.</p>
     *
     * @param name the name of the batch operation to cancel
     * @throws dev.langchain4j.exception.HttpException if the batch cannot be cancelled (e.g., already completed,
     *                                                 already cancelled, or does not exist)
     */
    public void cancelBatchJob(BatchName name) {
        geminiService.batchCancelBatch(name.value());
    }

    /**
     * Deletes a batch job from the system.
     * <p>
     * This removes the batch job but does not cancel it if still running.
     * Use {@link #cancelBatchJob(BatchName)} to cancel a running batch.
     *
     * @param name the name of the batch job to delete
     * @throws RuntimeException if the batch job cannot be deleted or does not exist
     */
    public void deleteBatchJob(BatchName name) {
        geminiService.batchDeleteBatch(name.value());
    }

    /**
     * Lists batch jobs with optional pagination.
     *
     * @param pageSize  the maximum number of batch jobs to return; if null, uses server default
     * @param pageToken token for retrieving a specific page from {@link BatchList#pageToken()};
     *                  if null, returns the first page
     * @return a {@link BatchList} containing batch responses and a token for the next page
     * @throws RuntimeException if the server does not support this operation
     */
    public BatchList listBatchJobs(@Nullable Integer pageSize, @Nullable String pageToken) {
        var response = geminiService.batchListBatches(pageSize, pageToken);
        return new BatchList(
                response.nextPageToken(),
                response.operations().stream().map(this::processResponse).toList());
    }

    private ChatRequest applyDefaultParameters(ChatRequest chatRequest) {
        return ChatRequest.builder()
                .messages(chatRequest.messages())
                .parameters(defaultRequestParameters.overrideWith(chatRequest.parameters()))
                .build();
    }

    private static String extractModelFromRequests(List<ChatRequest> requests) {
        var modelNames = requests.stream().map(ChatRequest::modelName).collect(Collectors.toUnmodifiableSet());
        if (modelNames.size() != 1) {
            throw new IllegalArgumentException("Batch requests cannot contain ChatRequest objects with different "
                    + "models; all requests must use the same model.");
        }

        return modelNames.iterator().next();
    }

    private BatchResponse processResponse(Operation operation) {
        if (operation.done()) {
            if (operation.error() != null) {
                var state = extractBatchState(operation.metadata());
                return new BatchError(
                        new BatchName(operation.name()),
                        operation.error().code(),
                        operation.error().message(),
                        state,
                        operation.error().details());
            } else {
                List<ChatResponse> responses = extractResponses(operation.response());
                return new BatchSuccess(new BatchName(operation.name()), responses);
            }
        } else {
            var state = extractBatchState(operation.metadata());
            return new BatchIncomplete(new BatchName(operation.name()), state);
        }
    }

    private List<ChatResponse> extractResponses(Response response) {
        if (response == null || response.inlinedResponses() == null) {
            return List.of();
        }

        return response.inlinedResponses().inlinedResponses().stream()
                .map(BatchGenerateContentResponse.InlinedResponseWrapper::response)
                .map(this::processResponse)
                .toList();
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

    static Builder builder() {
        return new Builder();
    }

    public static final class Builder extends GoogleAiGeminiChatModelBaseBuilder<Builder> {

        private Builder() {
        }

        public GoogleAiGeminiBatchChatModel build() {
            return new GoogleAiGeminiBatchChatModel(this);
        }
    }
}
