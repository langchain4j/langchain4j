package dev.langchain4j.model.google.genai;

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
import dev.langchain4j.model.google.genai.GoogleGenAiBatchRequestResponse.BatchError;
import dev.langchain4j.model.google.genai.GoogleGenAiBatchRequestResponse.BatchIncomplete;
import dev.langchain4j.model.google.genai.GoogleGenAiBatchRequestResponse.BatchJobState;
import dev.langchain4j.model.google.genai.GoogleGenAiBatchRequestResponse.BatchList;
import dev.langchain4j.model.google.genai.GoogleGenAiBatchRequestResponse.BatchName;
import dev.langchain4j.model.google.genai.GoogleGenAiBatchRequestResponse.BatchResponse;
import dev.langchain4j.model.google.genai.GoogleGenAiBatchRequestResponse.BatchSuccess;
import dev.langchain4j.model.google.genai.GoogleGenAiBatchRequestResponse.Status;
import dev.langchain4j.model.google.genai.GoogleGenAiEmbeddingModel.TaskTypeEnum;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Provides an interface for interacting with the Google GenAI Batch API for Embedding models.
 */
@Experimental
public final class GoogleGenAiBatchEmbeddingModel {

    private final Client client;
    private final String modelName;

    private final Integer outputDimensionality;
    private final TaskTypeEnum taskType;
    private final String titleMetadataKey;

    private GoogleGenAiBatchEmbeddingModel(Builder builder) {
        this.modelName = getOrDefault(builder.modelName, "gemini-embedding-2");
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

    /**
     * Creates and enqueues a batch of embedding requests for asynchronous processing.
     *
     * @param displayName a user-defined name for the batch
     * @param priority    not explicitly supported in google-genai Java SDK BatchJob creation, ignored.
     * @param requests    a list of text segments to be embedded in the batch
     * @return a {@link BatchResponse} representing the initial state of the batch operation
     */
    public BatchResponse<Embedding> createBatchInline(String displayName, Long priority, List<TextSegment> requests) {

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

        BatchJob batchJob = client.batches.createEmbeddings(modelName, src, config);
        return processResponse(batchJob);
    }

    /**
     * Creates a batch of embedding requests from an uploaded file.
     *
     * @param displayName a user-defined name for the batch
     * @param file        the Google GenAI File object representing the uploaded file containing batch requests
     * @return a {@link BatchResponse} representing the initial state of the batch operation
     */
    public BatchResponse<Embedding> createBatchFromFile(String displayName, File file) {
        EmbeddingsBatchJobSource src = EmbeddingsBatchJobSource.builder()
                .fileName(file.name().isPresent() ? file.name().get() : null)
                .build();

        CreateEmbeddingsBatchJobConfig config = CreateEmbeddingsBatchJobConfig.builder()
                .displayName(displayName)
                .build();

        BatchJob batchJob = client.batches.createEmbeddings(modelName, src, config);
        return processResponse(batchJob);
    }

    /**
     * Retrieves the current state and results of a batch operation.
     */
    public BatchResponse<Embedding> retrieveBatchResults(BatchName name) {
        BatchJob batchJob =
                client.batches.get(name.value(), GetBatchJobConfig.builder().build());
        return processResponse(batchJob);
    }

    /**
     * Cancels a batch operation that is currently pending or running.
     */
    public void cancelBatchJob(BatchName name) {
        client.batches.cancel(name.value(), CancelBatchJobConfig.builder().build());
    }

    /**
     * Deletes a batch job from the system.
     */
    public void deleteBatchJob(BatchName name) {
        client.batches.delete(name.value(), DeleteBatchJobConfig.builder().build());
    }

    /**
     * Lists batch jobs.
     */
    public BatchList<Embedding> listBatchJobs(Integer pageSize, String pageToken) {
        throw new UnsupportedOperationException("Batch List requires Pager API adaptation. Not fully supported yet.");
    }

    private BatchResponse<Embedding> processResponse(BatchJob batchJob) {
        String jobName = batchJob.name().orElse("unknown");
        Known state = batchJob.state().map(JobState::knownEnum).orElse(Known.JOB_STATE_UNSPECIFIED);

        BatchJobState translatedState;
        try {
            translatedState = BatchJobState.valueOf(state.name());
        } catch (IllegalArgumentException e) {
            translatedState = BatchJobState.UNRECOGNIZED;
        }

        if (state == Known.JOB_STATE_SUCCEEDED) {
            List<Embedding> responses = new ArrayList<>();
            List<Status> errors = new ArrayList<>();

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
                            responses.add(Embedding.from(floatArray));
                        }
                    }
                    if (inlined.error().isPresent()) {
                        var error = inlined.error().get();
                        errors.add(new Status(
                                error.code().orElse(0), error.message().orElse(""), new ArrayList<>()));
                    }
                }
            }
            return new BatchSuccess<>(new BatchName(jobName), responses, errors);
        } else if (state == Known.JOB_STATE_FAILED) {
            Integer code = 0;
            String message = "Batch job failed";
            if (batchJob.error().isPresent()) {
                code = batchJob.error().get().code().orElse(0);
                message = batchJob.error().get().message().orElse("Batch job failed");
            }
            return new BatchError<>(new BatchName(jobName), code, message, translatedState, new ArrayList<>());
        } else {
            return new BatchIncomplete<>(new BatchName(jobName), translatedState);
        }
    }

    public static class Builder {
        private Client client;
        private GoogleCredentials googleCredentials;
        private String apiKey;
        private String projectId;
        private String location;
        private String modelName;
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
