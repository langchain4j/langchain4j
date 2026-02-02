package dev.langchain4j.model.googleai;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.batch.BatchChatModel;
import dev.langchain4j.model.batch.BatchJobState;
import dev.langchain4j.model.batch.BatchList;
import dev.langchain4j.model.batch.BatchName;
import dev.langchain4j.model.batch.BatchResponse;
import dev.langchain4j.model.batch.ExtractedBatchResults;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchCreateResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchFileRequest;
import dev.langchain4j.model.googleai.GeminiFiles.GeminiFile;
import dev.langchain4j.model.googleai.jsonl.JsonLinesWriter;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static dev.langchain4j.model.googleai.BaseGeminiChatModel.buildGeminiService;
import static dev.langchain4j.model.googleai.GeminiService.BatchOperationType.BATCH_GENERATE_CONTENT;

/**
 * Provides an interface for interacting with the Gemini Batch API, an asynchronous service designed for processing
 * large volumes of requests at a reduced cost (50% of standard). It is ideal for non-urgent, large-scale tasks like
 * data pre-processing or evaluations, with a Service Level Objective (SLO) of 24-hour turnaround, though
 * completion is often much quicker.
 *
 * <p>Implements {@link BatchChatModel} for unified batch processing of chat requests.</p>
 *
 * @see BatchChatModel
 * @see BatchResponse
 */
@Experimental
public final class GoogleAiGeminiBatchChatModel implements BatchChatModel {

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
     * {@inheritDoc}
     *
     * <p>Creates and enqueues a batch of content generation requests for asynchronous processing
     * using default display name and priority.</p>
     *
     * @param requests a list of chat requests to be processed in the batch; all requests must
     *                 use the same model
     * @return a {@link BatchResponse} representing the initial state of the batch operation
     * @throws IllegalArgumentException if the requests contain different models
     */
    @Override
    public BatchResponse<ChatResponse> createBatch(List<ChatRequest> requests) {
        return createBatch(null, null, requests);
    }

    /**
     * Creates and enqueues a batch of content generation requests for asynchronous processing.
     *
     * <p>This method submits multiple chat requests as a single batch operation to the Gemini API.
     * All requests in the batch must use the same model. The batch will be processed asynchronously,
     * and the initial responses will typically be in an incomplete state.</p>
     *
     * <p>Batch processing offers a 50% cost reduction compared to real-time requests and has a
     * 24-hour turnaround SLO, making it ideal for large-scale, non-urgent tasks.</p>
     *
     * <p><strong>Note:</strong> The inline API allows for a total request size of 20MB or under.
     * Larger requests should use {@link #createBatchFromFile(String, GeminiFile)}.</p>
     *
     * @param displayName a user-defined name for the batch, used for identification; may be {@code null}
     * @param priority    optional priority for the batch; batches with higher priority values are
     *                    processed before those with lower values; negative values are allowed;
     *                    defaults to 0 if {@code null}
     * @param requests    a list of chat requests to be processed in the batch; all requests must
     *                    use the same model
     * @return a {@link BatchResponse} representing the initial state of the batch operation
     * @throws IllegalArgumentException if the requests contain different models
     */
    public BatchResponse<ChatResponse> createBatch(
            @Nullable String displayName, @Nullable Long priority, List<ChatRequest> requests) {
        validateModelInChatRequests(modelName, requests);
        return batchProcessor.createBatch(displayName, priority, requests, modelName, BATCH_GENERATE_CONTENT);
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
     * @return a {@link BatchResponse} representing the initial state of the batch operation
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
     * {@inheritDoc}
     *
     * <p>Polls the Gemini API to get the latest state of a previously created batch.
     * Clients should poll this method at intervals to check the operation status until completion.</p>
     *
     * @param name the batch name obtained from {@link #createBatch(List)} or {@link #createBatchFromFile(String, GeminiFile)}
     * @return a {@link BatchResponse} representing the current state of the batch operation
     */
    @Override
    public BatchResponse<ChatResponse> retrieveBatchResults(BatchName name) {
        return batchProcessor.retrieveBatchResults(name);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Cancellation is only possible for batches that are in
     * {@link BatchJobState#BATCH_STATE_PENDING} or {@link BatchJobState#BATCH_STATE_RUNNING} state.
     * Batches that have already completed, failed, or been cancelled cannot be cancelled.</p>
     *
     * @param name the batch name to cancel
     * @throws dev.langchain4j.exception.HttpException if the batch cannot be cancelled (e.g., already completed,
     *                                                 already cancelled, or does not exist)
     */
    @Override
    public void cancelBatchJob(BatchName name) {
        batchProcessor.cancelBatchJob(name);
    }

    /**
     * Deletes a batch job from the system.
     *
     * <p>This removes the batch job record but does not cancel it if still running.
     * Use {@link #cancelBatchJob(BatchName)} to cancel a running batch before deletion.</p>
     *
     * @param name the batch name to delete
     * @throws RuntimeException if the batch job cannot be deleted or does not exist
     */
    public void deleteBatchJob(BatchName name) {
        batchProcessor.deleteBatchJob(name);
    }

    /**
     * {@inheritDoc}
     *
     * @param pageSize  the maximum number of batch jobs to return; if {@code null}, uses server default
     * @param pageToken token for retrieving a specific page from {@link BatchList#nextPageToken()};
     *                  if {@code null}, returns the first page
     * @return a {@link BatchList} containing batch responses and pagination information
     */
    @Override
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

    /**
     * Returns a new builder for constructing {@link GoogleAiGeminiBatchChatModel} instances.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing {@link GoogleAiGeminiBatchChatModel} instances.
     */
    public static final class Builder extends BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder<Builder> {
        private Builder() {
        }

        /**
         * Builds a new {@link GoogleAiGeminiBatchChatModel} instance.
         *
         * @return the configured batch chat model
         */
        public GoogleAiGeminiBatchChatModel build() {
            return new GoogleAiGeminiBatchChatModel(this);
        }
    }

    private class ChatRequestPreparer
            implements GeminiBatchProcessor.RequestPreparer<
            ChatRequest, GeminiGenerateContentRequest, GeminiGenerateContentResponse, ChatResponse> {
        private static final TypeReference<BatchCreateResponse.InlinedResponseWrapper<GeminiGenerateContentResponse>>
                responseWrapperType = new TypeReference<>() {
        };
        private static final TypeReference<BatchCreateResponse<GeminiGenerateContentResponse>> responseTypeReference =
                new TypeReference<>() {
                };

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

        private static final Logger logger = LoggerFactory.getLogger(GoogleAiGeminiBatchChatModel.class);

        @Override
        public ExtractedBatchResults<ChatResponse> extractResults(
                BatchCreateResponse<GeminiGenerateContentResponse> response) {
            logger.debug("extractResults called with response: {}", response);

            if (response == null) {
                logger.warn("Response is null");
                return new ExtractedBatchResults<>(List.of(), List.of());
            }

            if (response.inlinedResponses() == null) {
                logger.warn("InlinedResponses is null");
                return new ExtractedBatchResults<>(List.of(), List.of());
            }

            List<ChatResponse> responses = new ArrayList<>();
            List<ExtractedBatchResults.Status> errors = new ArrayList<>();

            var inlinedResponses = response.inlinedResponses().inlinedResponses();
            if (inlinedResponses == null) {
                logger.warn("InlinedResponses.inlinedResponses() is null");
                return new ExtractedBatchResults<>(List.of(), List.of());
            }

            logger.debug("Processing {} inlined responses", inlinedResponses.size());

            for (int i = 0; i < inlinedResponses.size(); i++) {
                var wrapper = inlinedResponses.get(i);
                logger.debug("Wrapper [{}] class: {}", i, wrapper.getClass().getName());
                logger.debug("Wrapper [{}] value: {}", i, wrapper);

                BatchCreateResponse.InlinedResponseWrapper<GeminiGenerateContentResponse> typed;
                try {
                    typed = Json.convertValue(wrapper, responseWrapperType);
                    logger.debug("Typed [{}]: response={}, error={}", i, typed.response(), typed.error());
                } catch (Exception e) {
                    logger.error("Failed to convert wrapper [{}]: {}", i, e.getMessage(), e);
                    continue;
                }

                if (typed.response() != null) {
                    logger.debug("Processing response [{}]", i);
                    responses.add(chatModel.processResponse(typed.response()));
                } else {
                    logger.debug("Response [{}] is null", i);
                }

                var error = typed.error();
                if (error != null) {
                    logger.debug("Error [{}]: code={}, message={}", i, error.code(), error.message());
                    errors.add(new ExtractedBatchResults.Status(
                            error.code(),
                            error.message(),
                            error.details()
                    ));
                }
            }

            logger.debug("Extracted {} responses and {} errors", responses.size(), errors.size());
            return new ExtractedBatchResults<>(responses, errors);
        }

        @Override
        public TypeReference<BatchCreateResponse<GeminiGenerateContentResponse>> getResponseTypeReference() {
            return responseTypeReference;
        }
    }
}
