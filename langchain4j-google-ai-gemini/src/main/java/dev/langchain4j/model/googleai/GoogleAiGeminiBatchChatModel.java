package dev.langchain4j.model.googleai;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchCreateResponse;
import dev.langchain4j.model.googleai.GeminiBatchProcessor.BatchIncomplete;
import dev.langchain4j.model.googleai.GeminiBatchProcessor.BatchName;
import dev.langchain4j.model.googleai.GeminiBatchProcessor.BatchResponse;
import java.util.List;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Provides an interface for interacting with the Gemini Batch API, an asynchronous service designed for processing
 * large volumes of requests at a reduced cost (50% of standard). It is ideal for non-urgent, large-scale tasks like
 * data pre-processing or evaluations, with a Service Level Objective (SLO) of 24-hour turnaround, though
 * completion is often much quicker.
 */
@Experimental
public final class GoogleAiGeminiBatchChatModel {
    private final GeminiBatchProcessor<
                    ChatRequest, ChatResponse, GeminiGenerateContentRequest, GeminiGenerateContentResponse>
            batchProcessor;
    private final BaseGeminiChatModel chatModel;

    GoogleAiGeminiBatchChatModel(final Builder builder) {
        this(builder, BaseGeminiChatModel.buildGeminiService(builder));
    }

    GoogleAiGeminiBatchChatModel(final Builder builder, final GeminiService geminiService) {
        this.batchProcessor = new GeminiBatchProcessor<>(geminiService);
        this.chatModel = new BaseGeminiChatModel(builder, geminiService);
    }

    /***
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
    public BatchResponse<ChatResponse> createBatchInline(
            String displayName, @Nullable Long priority, List<ChatRequest> requests) {
        var modelName = extractModelFromChatRequests(requests);
        return batchProcessor.createBatchInline(displayName, priority, requests, modelName, new ChatRequestPreparer());
    }

    /**
     * Retrieves the current state and results of a batch operation.
     */
    public BatchResponse<ChatResponse> retrieveBatchResults(BatchName name) {
        return batchProcessor.retrieveBatchResults(name, new ChatRequestPreparer());
    }

    /**
     * Cancels a batch operation that is currently pending or running.
     */
    public void cancelBatchJob(BatchName name) {
        batchProcessor.cancelBatchJob(name);
    }

    /**
     * Deletes a batch job.
     */
    public void deleteBatchJob(BatchName name) {
        batchProcessor.deleteBatchJob(name);
    }

    /**
     * Lists batch jobs.
     */
    public void listBatchJobs() {
        batchProcessor.listBatchJobs();
    }

    private static String extractModelFromChatRequests(List<ChatRequest> requests) {
        var modelNames = requests.stream().map(ChatRequest::modelName).collect(Collectors.toUnmodifiableSet());

        if (modelNames.size() != 1) {
            throw new IllegalArgumentException(
                    "Batch requests cannot contain ChatRequest objects with different models; "
                            + "all requests must use the same model.");
        }

        return modelNames.iterator().next();
    }

    static Builder builder() {
        return new Builder();
    }

    public static final class Builder extends BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder<Builder> {
        private Builder() {}

        public GoogleAiGeminiBatchChatModel build() {
            return new GoogleAiGeminiBatchChatModel(this);
        }
    }

    private class ChatRequestPreparer
            implements GeminiBatchProcessor.RequestPreparer<
                    ChatRequest, GeminiGenerateContentRequest, GeminiGenerateContentResponse, ChatResponse> {

        @Override
        public ChatRequest prepareRequest(ChatRequest request) {
            return ChatRequest.builder()
                    .messages(request.messages())
                    .parameters(chatModel.defaultRequestParameters.overrideWith(request.parameters()))
                    .build();
        }

        @Override
        public GeminiGenerateContentRequest createInlinedRequest(ChatRequest request) {
            return chatModel.createGenerateContentRequest(request);
        }

        @Override
        public List<ChatResponse> extractResponses(BatchCreateResponse<GeminiGenerateContentResponse> response) {
            if (response == null || response.inlinedResponses() == null) {
                return List.of();
            }

            return response.inlinedResponses().inlinedResponses().stream()
                    .map(BatchCreateResponse.InlinedResponseWrapper::response)
                    .map(chatModel::processResponse)
                    .toList();
        }
    }
}
