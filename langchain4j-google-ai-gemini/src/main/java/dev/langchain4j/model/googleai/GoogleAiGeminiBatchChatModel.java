package dev.langchain4j.model.googleai;

import static dev.langchain4j.model.googleai.BaseGeminiChatModel.buildGeminiService;
import static dev.langchain4j.model.googleai.GeminiService.BatchOperationType.BATCH_GENERATE_CONTENT;

import com.fasterxml.jackson.core.type.TypeReference;
import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchCreateResponse;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchFileRequest;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchIncomplete;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchList;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchName;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchResponse;
import dev.langchain4j.model.googleai.GeminiBatchProcessor.ExtractedBatchResults;
import dev.langchain4j.model.googleai.GeminiFiles.GeminiFile;
import dev.langchain4j.model.googleai.jsonl.JsonLinesWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
    private final String modelName;
    private final ChatRequestPreparer preparer;

    GoogleAiGeminiBatchChatModel(final Builder builder) {
        this(builder, buildGeminiService(builder));
    }

    GoogleAiGeminiBatchChatModel(final Builder builder, final GeminiService geminiService) {
        this.preparer = new ChatRequestPreparer();
        this.batchProcessor = new GeminiBatchProcessor<>(geminiService, preparer);
        this.chatModel = new BaseGeminiChatModel(builder, geminiService);
        this.modelName = builder.modelName;
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
    public BatchResponse<ChatResponse> createBatchInline(
            String displayName, @Nullable Long priority, List<ChatRequest> requests) {
        validateModelInChatRequests(modelName, requests);
        return batchProcessor.createBatchInline(displayName, priority, requests, modelName, BATCH_GENERATE_CONTENT);
    }

    /**
     * Creates a batch of chat requests from an uploaded file.
     *
     * <p>This method allows you to create a batch job using a JSONL file that has been previously
     * uploaded to the Gemini Files API. This is useful for larger batches that exceed the 20 MB
     * inline request limit.</p>
     *
     * <p>The file must contain batch requests in JSONL format, where each line is a JSON object
     * with a "key" and "request" field. You can use {@link #writeBatchToFile(JsonLinesWriter, Iterable)}
     * to create properly formatted JSONL files.</p>
     *
     * @param displayName a user-defined name for the batch, used for identification
     * @param file        the GeminiFile object representing the uploaded file containing batch requests
     * @return a {@link BatchResponse} representing the initial state of the batch operation,
     * typically {@link BatchIncomplete}
     * @see #writeBatchToFile(JsonLinesWriter, Iterable)
     * @see GeminiFiles#uploadFile(java.nio.file.Path, String)
     */
    public BatchResponse<ChatResponse> createBatchFromFile(String displayName, GeminiFile file) {
        return batchProcessor.createBatchFromFile(displayName, file, modelName, BATCH_GENERATE_CONTENT);
    }

    /**
     * Writes a batch of chat requests to a JSONL file for later upload and processing.
     *
     * <p>This method serializes chat requests into JSONL (JSON Lines) format, where each line
     * contains a single request wrapped in a {@link BatchFileRequest} with a unique key.
     * The resulting file can be uploaded using the Gemini Files API and then used to create
     * a batch job via {@link #createBatchFromFile(String, GeminiFile)}.</p>
     *
     * <p>Each request is converted to the internal Gemini format before being written to the file.</p>
     *
     * <p><strong>Example usage:</strong></p>
     * <pre>{@code
     * Path batchFile = Files.createTempFile("batch", ".jsonl");
     * try (JsonLinesWriter writer = new StreamingJsonLinesWriter(batchFile)) {
     *     List<BatchFileRequest<ChatRequest>> requests = List.of(
     *         new BatchFileRequest<>("request-1", ChatRequest.builder()
     *             .messages(UserMessage.from("Question 1"))
     *             .build()),
     *         new BatchFileRequest<>("request-2", ChatRequest.builder()
     *             .messages(UserMessage.from("Question 2"))
     *             .build())
     *     );
     *     batchModel.writeBatchToFile(writer, requests);
     * }
     * }</pre>
     *
     * @param writer   the JsonLinesWriter to which the batch requests will be written
     * @param requests an iterable collection of BatchFileRequest objects containing ChatRequest instances,
     *                 each with a unique key identifier
     * @throws IOException if an I/O error occurs while writing to the writer
     * @see #createBatchFromFile(String, GeminiFile)
     * @see JsonLinesWriter
     */
    public void writeBatchToFile(JsonLinesWriter writer, Iterable<BatchFileRequest<ChatRequest>> requests)
            throws IOException {
        for (var request : requests) {
            var inlinedRequest = preparer.createInlinedRequest(request.request());
            writer.write(new BatchFileRequest<>(request.key(), inlinedRequest));
        }
    }

    /**
     * Retrieves the current state and results of a batch operation.
     *
     * <p>This method polls the Gemini API to get the latest state of a previously created batch.
     * The response can be:
     * <ul>
     *   <li>{@link BatchIncomplete} - if the batch is still pending or running</li>
     *   <li>{@link BatchRequestResponse.BatchSuccess} - if the batch completed successfully, containing all responses</li>
     *   <li>{@link BatchRequestResponse.BatchError} - if the batch failed, containing error details</li>
     * </ul>
     * <p>
     * Clients should poll this method at intervals to check the operation status until completion.</p>
     *
     * @param name the name of the batch operation to retrieve, obtained from the initial
     *             {@link #createBatchInline} call
     * @return a {@link BatchResponse} representing the current state of the batch operation
     */
    public BatchResponse<ChatResponse> retrieveBatchResults(BatchName name) {
        return batchProcessor.retrieveBatchResults(name);
    }

    /**
     * Cancels a batch operation that is currently pending or running.
     *
     * <p>This method attempts to cancel a batch job. Cancellation is only possible for batches
     * that are in {@link BatchRequestResponse.BatchJobState#BATCH_STATE_PENDING} or {@link BatchRequestResponse.BatchJobState#BATCH_STATE_RUNNING}
     * state. Batches that have already completed, failed, or been cancelled cannot be cancelled.</p>
     *
     * @param name the name of the batch operation to cancel
     * @throws dev.langchain4j.exception.HttpException if the batch cannot be cancelled (e.g., already completed,
     *                                                 already cancelled, or does not exist)
     */
    public void cancelBatchJob(BatchName name) {
        batchProcessor.cancelBatchJob(name);
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
        batchProcessor.deleteBatchJob(name);
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
    public BatchList<ChatResponse> listBatchJobs(@Nullable Integer pageSize, @Nullable String pageToken) {
        return batchProcessor.listBatchJobs(pageSize, pageToken);
    }

    private static void validateModelInChatRequests(String modelName, List<ChatRequest> requests) {
        var modelNames = Stream.concat(requests.stream().map(ChatRequest::modelName), Stream.of(modelName))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (modelNames.size() != 1) {
            throw new IllegalArgumentException(
                    "Batch requests cannot contain ChatRequest objects with different models; "
                            + "all requests must use the same model: " + modelNames);
        }
    }

    public static Builder builder() {
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
        private static final TypeReference<BatchCreateResponse.InlinedResponseWrapper<GeminiGenerateContentResponse>>
                responseWrapperType = new TypeReference<>() {};

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
        public ExtractedBatchResults<ChatResponse> extractResults(
                BatchCreateResponse<GeminiGenerateContentResponse> response) {
            if (response == null || response.inlinedResponses() == null) {
                return new ExtractedBatchResults<>(List.of(), List.of());
            }

            List<ChatResponse> responses = new ArrayList<>();
            List<BatchRequestResponse.Operation.Status> errors = new ArrayList<>();

            for (Object wrapper : response.inlinedResponses().inlinedResponses()) {
                var typed = Json.convertValue(wrapper, responseWrapperType);
                if (typed.response() != null) {
                    responses.add(chatModel.processResponse(typed.response()));
                }
                if (typed.error() != null) {
                    errors.add(typed.error());
                }
            }

            return new ExtractedBatchResults<>(responses, errors);
        }
    }
}
