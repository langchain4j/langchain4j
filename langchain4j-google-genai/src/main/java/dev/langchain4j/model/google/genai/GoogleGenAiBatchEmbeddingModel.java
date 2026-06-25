package dev.langchain4j.model.google.genai;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;
import com.google.genai.types.BatchJob;
import com.google.genai.types.CancelBatchJobConfig;
import com.google.genai.types.Content;
import com.google.genai.types.CreateEmbeddingsBatchJobConfig;
import com.google.genai.types.DeleteBatchJobConfig;
import com.google.genai.types.EmbedContentBatch;
import com.google.genai.types.EmbedContentConfig;
import com.google.genai.types.EmbeddingsBatchJobSource;
import com.google.genai.types.File;
import com.google.genai.types.GetBatchJobConfig;
import com.google.genai.types.JobState;
import com.google.genai.types.JobState.Known;
import com.google.genai.types.Part;
import dev.langchain4j.Experimental;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.batch.BatchItemResult;
import dev.langchain4j.model.batch.BatchPage;
import dev.langchain4j.model.batch.BatchPagination;
import dev.langchain4j.model.batch.BatchRequest;
import dev.langchain4j.model.batch.BatchResponse;
import dev.langchain4j.model.batch.BatchState;
import dev.langchain4j.model.embedding.BatchEmbeddingModel;
import dev.langchain4j.model.google.genai.GoogleGenAiEmbeddingModel.TaskTypeEnum;
import dev.langchain4j.model.output.Response;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Provides an interface for interacting with the Google GenAI Batch API for Embedding models.
 */
@Experimental
public final class GoogleGenAiBatchEmbeddingModel implements BatchEmbeddingModel {

    private final Client client;
    private final String modelName;
    private final Integer maxRetries;

    private final Integer outputDimensionality;
    private final TaskTypeEnum taskType;
    private final String titleMetadataKey;

    private GoogleGenAiBatchEmbeddingModel(Builder builder) {
        this.modelName = ensureNotBlank(builder.modelName, "modelName");
        this.maxRetries = getOrDefault(builder.maxRetries, 3);
        this.outputDimensionality = builder.outputDimensionality;
        this.taskType = builder.taskType;
        this.titleMetadataKey = builder.titleMetadataKey;

        this.client = builder.client != null
                ? builder.client
                : GoogleGenAiClientFactory.createClient(
                        builder.apiKey,
                        builder.googleCredentials,
                        builder.projectId,
                        builder.location,
                        builder.timeout,
                        builder.customHeaders,
                        builder.apiEndpoint);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public BatchResponse<Response<Embedding>> submit(BatchRequest<TextSegment> request) {
        String timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss")
                .withZone(ZoneId.systemDefault())
                .format(Instant.now());
        return submit("batch-embedding-job-" + timestamp, request.requests());
    }

    @Override
    public BatchResponse<Response<Embedding>> retrieve(String batchId) {
        BatchJob batchJob =
                client.batches.get(batchId, GetBatchJobConfig.builder().build());
        return processResponse(batchJob);
    }

    @Override
    public void cancel(String batchId) {
        client.batches.cancel(batchId, CancelBatchJobConfig.builder().build());
    }

    @Override
    public BatchPage<Response<Embedding>> list(BatchPagination pagination) {
        Integer pageSize = pagination != null ? pagination.pageSize() : null;
        String pageToken = pagination != null ? pagination.pageToken() : null;
        return GoogleGenAiBatchUtils.listBatchJobs(client, pageSize, pageToken, this::processResponse);
    }

    /**
     * Creates and enqueues a batch of embedding requests for asynchronous processing.
     *
     * @param displayName a user-defined name for the batch
     * @param requests    a list of text segments to be embedded in the batch
     * @return a {@link BatchResponse} representing the initial state of the batch operation
     */
    public BatchResponse<Response<Embedding>> submit(String displayName, List<TextSegment> requests) {
        List<Content> contents = requests.stream()
                .map(segment -> Content.builder()
                        .parts(List.of(Part.builder().text(segment.text()).build()))
                        .build())
                .collect(Collectors.toList());

        EmbedContentConfig.Builder configBuilder = EmbedContentConfig.builder();
        if (outputDimensionality != null) {
            configBuilder.outputDimensionality(outputDimensionality);
        }
        if (taskType != null) {
            configBuilder.taskType(taskType.getSdkTaskType());
        }

        EmbedContentBatch inlinedRequests = EmbedContentBatch.builder()
                .contents(contents)
                .config(configBuilder.build())
                .build();

        EmbeddingsBatchJobSource src = EmbeddingsBatchJobSource.builder()
                .inlinedRequests(inlinedRequests)
                .build();

        CreateEmbeddingsBatchJobConfig config = CreateEmbeddingsBatchJobConfig.builder()
                .displayName(displayName)
                .build();

        BatchJob batchJob =
                withRetryMappingExceptions(() -> client.batches.createEmbeddings(modelName, src, config), maxRetries);
        return processResponse(batchJob);
    }

    /**
     * Creates a batch of embedding requests from an uploaded file.
     *
     * @param displayName a user-defined name for the batch
     * @param file        the Google GenAI File object representing the uploaded file containing batch requests
     * @return a {@link BatchResponse} representing the initial state of the batch operation
     */
    public BatchResponse<Response<Embedding>> submit(String displayName, File file) {
        EmbeddingsBatchJobSource src = EmbeddingsBatchJobSource.builder()
                .fileName(file.name().isPresent() ? file.name().get() : null)
                .build();

        CreateEmbeddingsBatchJobConfig config = CreateEmbeddingsBatchJobConfig.builder()
                .displayName(displayName)
                .build();

        BatchJob batchJob =
                withRetryMappingExceptions(() -> client.batches.createEmbeddings(modelName, src, config), maxRetries);
        return processResponse(batchJob);
    }

    /**
     * Deletes a batch job from the system.
     */
    public void deleteBatchJob(String batchId) {
        client.batches.delete(batchId, DeleteBatchJobConfig.builder().build());
    }

    private BatchResponse<Response<Embedding>> processResponse(BatchJob batchJob) {
        String jobName = batchJob.name().orElse("unknown");
        Known state = batchJob.state().map(JobState::knownEnum).orElse(Known.JOB_STATE_UNSPECIFIED);

        BatchState translatedState = GoogleGenAiBatchUtils.toBatchState(state);

        BatchResponse.Builder<Response<Embedding>> builder =
                BatchResponse.<Response<Embedding>>builder().batchId(jobName).state(translatedState);

        if (state == Known.JOB_STATE_SUCCEEDED) {
            List<BatchItemResult<Response<Embedding>>> results = new ArrayList<>();
            if (batchJob.dest().isPresent()
                    && batchJob.dest().get().inlinedEmbedContentResponses().isPresent()) {
                var inlinedResponses =
                        batchJob.dest().get().inlinedEmbedContentResponses().get();
                for (var inlined : inlinedResponses) {
                    if (inlined.response().isPresent()) {
                        var embeddingOpt = inlined.response().get().embedding();
                        if (embeddingOpt.isPresent()
                                && embeddingOpt.get().values().isPresent()) {
                            var values = embeddingOpt.get().values().get();
                            float[] floatArray = new float[values.size()];
                            for (int i = 0; i < values.size(); i++) {
                                floatArray[i] = values.get(i).floatValue();
                            }
                            results.add(BatchItemResult.success(Response.from(Embedding.from(floatArray))));
                        }
                    } else if (inlined.error().isPresent()) {
                        results.add(BatchItemResult.failure(GoogleGenAiBatchUtils.toBatchError(
                                inlined.error().get())));
                    }
                }
            }
            builder.results(results);
        } else if (state == Known.JOB_STATE_FAILED) {
            builder.results(List.of(BatchItemResult.failure(
                    GoogleGenAiBatchUtils.toBatchError(batchJob.error().orElse(null)))));
        }

        return builder.build();
    }

    public static class Builder {
        private Client client;
        private GoogleCredentials googleCredentials;
        private String apiKey;
        private String projectId;
        private String location;
        private String modelName;
        private Integer maxRetries;
        private Duration timeout;
        private Integer outputDimensionality;
        private TaskTypeEnum taskType;
        private String titleMetadataKey;
        private String apiEndpoint;
        private Map<String, String> customHeaders;

        public Builder client(Client client) {
            this.client = client;
            return this;
        }

        public Builder googleCredentials(GoogleCredentials credentials) {
            this.googleCredentials = credentials;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        public Builder location(String location) {
            this.location = location;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = ensureNotBlank(modelName, "modelName");
            return this;
        }

        public Builder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder outputDimensionality(Integer outputDimensionality) {
            this.outputDimensionality = outputDimensionality;
            return this;
        }

        public Builder taskType(TaskTypeEnum taskType) {
            this.taskType = taskType;
            return this;
        }

        public Builder titleMetadataKey(String titleMetadataKey) {
            this.titleMetadataKey = titleMetadataKey;
            return this;
        }

        public Builder apiEndpoint(String apiEndpoint) {
            this.apiEndpoint = apiEndpoint;
            return this;
        }

        public Builder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        public GoogleGenAiBatchEmbeddingModel build() {
            return new GoogleGenAiBatchEmbeddingModel(this);
        }
    }
}
