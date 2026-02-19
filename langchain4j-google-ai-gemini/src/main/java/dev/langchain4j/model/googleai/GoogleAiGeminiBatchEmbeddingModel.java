package dev.langchain4j.model.googleai;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.googleai.GeminiService.BatchOperationType.ASYNC_BATCH_EMBED_CONTENT;

import com.fasterxml.jackson.core.type.TypeReference;
import dev.langchain4j.Experimental;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.batch.BatchError;
import dev.langchain4j.model.batch.BatchId;
import dev.langchain4j.model.batch.BatchPage;
import dev.langchain4j.model.batch.BatchRequest;
import dev.langchain4j.model.batch.BatchResponse;
import dev.langchain4j.model.embedding.BatchEmbeddingModel;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchCreateResponse;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchFileRequest;
import dev.langchain4j.model.googleai.GeminiEmbeddingRequestResponse.GeminiEmbeddingRequest;
import dev.langchain4j.model.googleai.GeminiEmbeddingRequestResponse.GeminiEmbeddingResponse;
import dev.langchain4j.model.googleai.GeminiFiles.GeminiFile;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel.BaseGoogleAiEmbeddingModelBuilder;
import dev.langchain4j.model.googleai.jsonl.JsonLinesWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Batch embedding model for Google AI Gemini.
 *
 * <p>Provides asynchronous batch processing for generating embeddings from multiple text segments
 * at reduced cost (50% of standard pricing) with a 24-hour turnaround SLO.</p>
 *
 * <p>Implements {@link BatchEmbeddingModel} for unified batch processing of embedding requests.</p>
 *
 * @see BatchEmbeddingModel
 * @see BatchResponse
 */
@Experimental
@NullMarked
public final class GoogleAiGeminiBatchEmbeddingModel implements BatchEmbeddingModel {
    private final GeminiBatchProcessor<TextSegment, Embedding, GeminiEmbeddingRequest, GeminiEmbeddingResponse>
            batchProcessor;
    private final String modelName;
    private final GoogleAiEmbeddingModel.TaskType taskType;
    private final String titleMetadataKey;
    private final Integer outputDimensionality;
    private final EmbeddingRequestPreparer preparer;

    GoogleAiGeminiBatchEmbeddingModel(final Builder builder) {
        this(
                builder,
                new GeminiService(
                        builder.httpClientBuilder,
                        builder.apiKey,
                        builder.baseUrl,
                        getOrDefault(builder.logRequestsAndResponses, false),
                        getOrDefault(builder.logRequests, false),
                        getOrDefault(builder.logResponses, false),
                        builder.logger,
                        builder.timeout));
    }

    GoogleAiGeminiBatchEmbeddingModel(Builder builder, final GeminiService geminiService) {
        this.preparer = new EmbeddingRequestPreparer();
        this.batchProcessor = new GeminiBatchProcessor<>(geminiService, preparer);
        this.modelName = builder.modelName;
        this.taskType = builder.taskType;
        this.titleMetadataKey = builder.titleMetadataKey != null ? builder.titleMetadataKey : "title";
        this.outputDimensionality = builder.outputDimensionality;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Creates and enqueues a batch of embedding requests using default display name and priority.</p>
     *
     * @param request the list of {@link TextSegment}s to generate embeddings for
     * @return a {@link BatchResponse} representing the initial state of the batch operation
     */
    @Override
    public BatchResponse<Embedding> submit(BatchRequest<TextSegment> request) {
        if (request instanceof GeminiBatchRequest<TextSegment> batchRequest) {
            return batchProcessor.createBatch(
                    batchRequest.displayName(),
                    batchRequest.priority(),
                    batchRequest.requests(),
                    modelName,
                    ASYNC_BATCH_EMBED_CONTENT);

        } else {
            return batchProcessor.createBatch(null, null, request.requests(), modelName, ASYNC_BATCH_EMBED_CONTENT);
        }
    }

    /**
     * Creates and enqueues a batch of embedding requests from an uploaded file.
     *
     * <p>This method is used for processing large volumes of embedding requests that exceed the limits
     * of the inline API (20 MB). Before calling this method, you must write your requests to a JSONL file
     * (using {@link #writeBatchToFile}) and upload it using the {@link GeminiFiles} API to get a {@link GeminiFile}.</p>
     *
     * @param displayName a user-defined name for the batch, used for identification
     * @param file        the {@link GeminiFile} representing the uploaded JSONL file containing the requests
     * @return a {@link BatchResponse} representing the initial state of the batch operation
     * @see #writeBatchToFile(JsonLinesWriter, Iterable)
     * @see GeminiFiles#uploadFile(java.nio.file.Path, String)
     */
    public BatchResponse<Embedding> submit(String displayName, GeminiFile file) {
        return batchProcessor.createBatchFromFile(displayName, file, modelName, ASYNC_BATCH_EMBED_CONTENT);
    }

    /**
     * Writes a sequence of text segments to a JSONL file writer in the format required for file-based batch processing.
     *
     * <p>This helper method takes high-level {@link TextSegment} objects wrapped in {@link BatchFileRequest}s
     * (which enable assigning unique keys to each request) and serializes them into the specific JSON structure
     * expected by the Gemini Batch API. It handles the conversion to internal request objects, including
     * metadata handling for document titles.</p>
     *
     * <p><strong>Example Usage:</strong></p>
     * <pre>{@code
     * Path batchFile = Files.createTempFile("embeddings", ".jsonl");
     * try (JsonLinesWriter writer = new StreamingJsonLinesWriter(batchFile)) {
     *     List<BatchFileRequest<TextSegment>> requests = List.of(
     *         new BatchFileRequest<>("doc-1", TextSegment.from("Content for document 1")),
     *         new BatchFileRequest<>("doc-2", TextSegment.from("Content for document 2"))
     *     );
     *     batchModel.writeBatchToFile(writer, requests);
     * }
     * }</pre>
     *
     * @param writer   the {@link JsonLinesWriter} to write to
     * @param requests an iterable of {@link BatchFileRequest}s, each containing a key and a {@link TextSegment}
     * @throws IOException if an error occurs while writing to the underlying stream
     */
    public void writeBatchToFile(JsonLinesWriter writer, Iterable<BatchFileRequest<TextSegment>> requests)
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
     * @param name the batch name obtained from {@link BatchEmbeddingModel#submit(BatchRequest)} or {@link #submit(String, GeminiFile)}
     * @return a {@link BatchResponse} representing the current state of the batch operation
     */
    @Override
    public BatchResponse<Embedding> retrieve(BatchId name) {
        return batchProcessor.retrieveBatchResults(name);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Cancellation is only possible for batches that are in PENDING or RUNNING state.
     * Batches that have already completed, failed, or been cancelled cannot be cancelled.</p>
     *
     * @param name the batch name to cancel
     */
    @Override
    public void cancel(BatchId name) {
        batchProcessor.cancelBatchJob(name);
    }

    /**
     * Deletes a batch job from the system.
     *
     * <p>This removes the batch job record but does not cancel it if still running.
     * Use {@link #cancel(BatchId)} to cancel a running batch before deletion.</p>
     *
     * @param name the batch name to delete
     * @throws RuntimeException if the batch job cannot be deleted or does not exist
     */
    public void deleteBatchJob(BatchId name) {
        batchProcessor.deleteBatchJob(name);
    }

    /**
     * {@inheritDoc}
     *
     * @param pageSize  the maximum number of batch jobs to return; if {@code null}, uses server default
     * @param pageToken token for retrieving a specific page from {@link BatchPage#nextPageToken()};
     *                  if {@code null}, returns the first page
     * @return a {@link BatchPage} containing batch responses and pagination information
     */
    @Override
    public BatchPage<Embedding> list(@Nullable Integer pageSize, @Nullable String pageToken) {
        return batchProcessor.listBatchJobs(pageSize, pageToken);
    }

    /**
     * Returns a new builder for constructing {@link GoogleAiGeminiBatchEmbeddingModel} instances.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing {@link GoogleAiGeminiBatchEmbeddingModel} instances.
     */
    public static class Builder extends BaseGoogleAiEmbeddingModelBuilder<Builder> {

        /**
         * Builds a new {@link GoogleAiGeminiBatchEmbeddingModel} instance.
         *
         * @return the configured batch embedding model
         */
        public GoogleAiGeminiBatchEmbeddingModel build() {
            return new GoogleAiGeminiBatchEmbeddingModel(this);
        }
    }

    private class EmbeddingRequestPreparer
            implements GeminiBatchProcessor.RequestPreparer<
                    TextSegment, GeminiEmbeddingRequest, GeminiEmbeddingResponse, Embedding> {
        private static final TypeReference<BatchCreateResponse.InlinedResponseWrapper<GeminiEmbeddingResponse>>
                responseWrapperType = new TypeReference<>() {};

        @Override
        public TextSegment prepareRequest(TextSegment textSegment) {
            // No preparation needed for embeddings - return as-is
            return textSegment;
        }

        @Override
        public GeminiEmbeddingRequest createInlinedRequest(TextSegment textSegment) {
            GeminiContent.GeminiPart geminiPart =
                    GeminiContent.GeminiPart.builder().text(textSegment.text()).build();

            GeminiContent content = new GeminiContent(Collections.singletonList(geminiPart), null);

            String title = null;
            if (GoogleAiEmbeddingModel.TaskType.RETRIEVAL_DOCUMENT.equals(taskType)
                    && textSegment.metadata() != null
                    && textSegment.metadata().getString(titleMetadataKey) != null) {
                title = textSegment.metadata().getString(titleMetadataKey);
            }

            return new GeminiEmbeddingRequest("models/" + modelName, content, taskType, title, outputDimensionality);
        }

        @Override
        public GeminiBatchProcessor.ExtractedBatchResults<Embedding> extractResults(
                @Nullable BatchCreateResponse<GeminiEmbeddingResponse> response) {
            if (response == null || response.inlinedResponses() == null) {
                return new GeminiBatchProcessor.ExtractedBatchResults<>(List.of(), List.of());
            }

            List<Embedding> responses = new ArrayList<>();
            List<BatchError> errors = new ArrayList<>();

            for (Object wrapper : response.inlinedResponses().inlinedResponses()) {
                var typed = Json.convertValue(wrapper, responseWrapperType);
                var typedResponse = typed.response();
                if (typedResponse != null) {
                    var embedding = Embedding.from(typedResponse.embedding().values());
                    responses.add(embedding);
                }
                var error = typed.error();
                if (error != null) {
                    errors.add(error.toGenericStatus());
                }
            }

            return new GeminiBatchProcessor.ExtractedBatchResults<>(responses, errors);
        }
    }
}
