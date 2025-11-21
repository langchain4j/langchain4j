package dev.langchain4j.model.googleai;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.googleai.GeminiService.BatchOperationType.ASYNC_BATCH_EMBED_CONTENT;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchCreateResponse;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchList;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchName;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchResponse;
import dev.langchain4j.model.googleai.GeminiEmbeddingRequestResponse.GeminiEmbeddingRequest;
import dev.langchain4j.model.googleai.GeminiEmbeddingRequestResponse.GeminiEmbeddingResponse;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel.BaseGoogleAiEmbeddingModelBuilder;
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
        this.batchProcessor = new GeminiBatchProcessor<>(geminiService, new EmbeddingRequestPreparer());
        this.modelName = builder.modelName;
        this.taskType = builder.taskType;
        this.titleMetadataKey = builder.titleMetadataKey != null ? builder.titleMetadataKey : "title";
        this.outputDimensionality = builder.outputDimensionality;
    }

    /**
     * Creates and enqueues a batch of embedding requests for asynchronous processing.
     */
    public BatchResponse<Embedding> createBatchInline(
            String displayName, @Nullable Long priority, List<TextSegment> segments) {
        return batchProcessor.createBatchInline(displayName, priority, segments, modelName, ASYNC_BATCH_EMBED_CONTENT);
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
        public List<Embedding> extractResponses(BatchCreateResponse<GeminiEmbeddingResponse> response) {
            if (response == null || response.inlinedResponses() == null) {
                return List.of();
            }

            return response.inlinedResponses().inlinedResponses().stream()
                    .map(BatchCreateResponse.InlinedResponseWrapper::response)
                    .map(GeminiEmbeddingResponse::embedding)
                    .map(contentEmbedding -> Embedding.from(contentEmbedding.values()))
                    .toList();
        }
    }
}
