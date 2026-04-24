package dev.langchain4j.model.googleai;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.googleai.GeminiService.BatchOperationType.ASYNC_BATCH_EMBED_CONTENT;

import com.fasterxml.jackson.core.type.TypeReference;
import dev.langchain4j.Experimental;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchCreateResponse;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchFileRequest;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchIncomplete;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchList;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchName;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchResponse;
import dev.langchain4j.model.googleai.GeminiBatchProcessor.ExtractedBatchResults;
import dev.langchain4j.model.googleai.GeminiEmbeddingRequestResponse.GeminiEmbeddingRequest;
import dev.langchain4j.model.googleai.GeminiEmbeddingRequestResponse.GeminiEmbeddingResponse;
import dev.langchain4j.model.googleai.GeminiFiles.GeminiFile;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel.BaseGoogleAiEmbeddingModelBuilder;
import dev.langchain4j.model.googleai.jsonl.JsonLinesWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Batch embedding model for Google AI Gemini.
 */
@Experimental
public final class GoogleAiGeminiBatchEmbeddingModel {
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
     * Creates and enqueues a batch of embedding requests for asynchronous processing using the inline API.
     *
     * <p>This method submits a list of text segments to be embedded as a single batch operation.
     * It is designed for efficient, asynchronous processing of multiple texts. This method uses the
     * inline batch creation endpoint, which supports requests up to 20 MB in size.</p>
     *
     * <p>The response contains the initial state of the batch job (usually PENDING). You can monitor
     * the job's progress using {@link #retrieveBatchResults(BatchName)}.</p>
     *
     * @param displayName a user-defined name for the batch, used for identification and listing
     * @param priority    optional priority for the batch; batches with higher priority values are
     *                    processed before those with lower values; negative values are allowed;
     *                    defaults to 0 if null
     * @param segments    the list of {@link TextSegment}s to generate embeddings for
     * @return a {@link BatchResponse} representing the initial state of the batch operation,
     * typically {@link BatchIncomplete}
     */
    public BatchResponse<Embedding> createBatchInline(
            String displayName, @Nullable Long priority, List<TextSegment> segments) {
        return batchProcessor.createBatchInline(displayName, priority, segments, modelName, ASYNC_BATCH_EMBED_CONTENT);
    }

    /**
     * Creates and enqueues a batch of embedding requests from an uploaded file.
     *
     * <p>This method is used for processing large volumes of embedding requests that exceed the limits
     * of the inline API. Before calling this method, you must write your requests to a JSONL file
     * (using {@link #writeBatchToFile}) and upload it using the {@link GeminiFiles} API to get a {@link GeminiFile}.</p>
     *
     * @param displayName a user-defined name for the batch, used for identification
     * @param file        the {@link GeminiFile} representing the uploaded JSONL file containing the requests
     * @return a {@link BatchResponse} representing the initial state of the batch operation
     * @see #writeBatchToFile(JsonLinesWriter, Iterable)
     * @see GeminiFiles#uploadFile(java.nio.file.Path, String)
     */
    public BatchResponse<Embedding> createBatchFromFile(String displayName, GeminiFile file) {
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
     * Retrieves the current state and results of a batch operation.
     */
    public BatchResponse<Embedding> retrieveBatchResults(BatchName name) {
        return batchProcessor.retrieveBatchResults(name);
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
    public BatchList<Embedding> listBatchJobs(@Nullable Integer pageSize, @Nullable String pageToken) {
        return batchProcessor.listBatchJobs(pageSize, pageToken);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends BaseGoogleAiEmbeddingModelBuilder<Builder> {
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
        public ExtractedBatchResults<Embedding> extractResults(BatchCreateResponse<GeminiEmbeddingResponse> response) {
            if (response == null || response.inlinedResponses() == null) {
                return new ExtractedBatchResults<>(List.of(), List.of());
            }

            List<Embedding> responses = new ArrayList<>();
            List<BatchRequestResponse.Operation.Status> errors = new ArrayList<>();

            for (Object wrapper : response.inlinedResponses().inlinedResponses()) {
                var typed = Json.convertValue(wrapper, responseWrapperType);
                if (typed.response() != null) {
                    var embedding = Embedding.from(typed.response().embedding().values());
                    responses.add(embedding);
                }
                if (typed.error() != null) {
                    errors.add(typed.error());
                }
            }

            return new ExtractedBatchResults<>(responses, errors);
        }
    }
}
