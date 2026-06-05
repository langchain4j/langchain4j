package dev.langchain4j.model.google.genai;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;
import com.google.genai.types.BatchJob;
import com.google.genai.types.BatchJobSource;
import com.google.genai.types.CancelBatchJobConfig;
import com.google.genai.types.Content;
import com.google.genai.types.CreateBatchJobConfig;
import com.google.genai.types.DeleteBatchJobConfig;
import com.google.genai.types.File;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GetBatchJobConfig;
import com.google.genai.types.ImageConfig;
import com.google.genai.types.InlinedRequest;
import com.google.genai.types.JobState;
import com.google.genai.types.JobState.Known;
import com.google.genai.types.Part;
import com.google.genai.types.SafetySetting;
import dev.langchain4j.Experimental;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.batch.BatchError;
import dev.langchain4j.model.batch.BatchItemResult;
import dev.langchain4j.model.batch.BatchPage;
import dev.langchain4j.model.batch.BatchPagination;
import dev.langchain4j.model.batch.BatchRequest;
import dev.langchain4j.model.batch.BatchResponse;
import dev.langchain4j.model.batch.BatchState;
import dev.langchain4j.model.image.BatchImageModel;
import dev.langchain4j.model.output.Response;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Provides an interface for interacting with the Google GenAI Batch API for Image generation models.
 */
@Experimental
public final class GoogleGenAiBatchImageModel implements BatchImageModel {

    private final Client client;
    private final String modelName;
    private final Integer maxRetries;

    private final List<SafetySetting> safetySettings;
    private final String aspectRatio;
    private final String imageSize;
    private final String personGeneration;
    private final Map<String, String> labels;

    private GoogleGenAiBatchImageModel(Builder builder) {
        this.modelName = ensureNotBlank(builder.modelName, "modelName");
        this.maxRetries = getOrDefault(builder.maxRetries, 3);
        this.safetySettings = builder.safetySettings != null ? new ArrayList<>(builder.safetySettings) : null;
        this.aspectRatio = builder.aspectRatio;
        this.imageSize = builder.imageSize;
        this.personGeneration = builder.personGeneration;
        this.labels = builder.labels != null ? new HashMap<>(builder.labels) : null;

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
    public BatchResponse<Response<Image>> submit(BatchRequest<String> request) {
        String timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss")
                .withZone(ZoneId.systemDefault())
                .format(Instant.now());
        return submit("batch-image-job-" + timestamp, request.requests());
    }

    @Override
    public BatchResponse<Response<Image>> retrieve(String batchId) {
        BatchJob batchJob =
                client.batches.get(batchId, GetBatchJobConfig.builder().build());
        return processResponse(batchJob);
    }

    @Override
    public void cancel(String batchId) {
        client.batches.cancel(batchId, CancelBatchJobConfig.builder().build());
    }

    @Override
    public BatchPage<Response<Image>> list(BatchPagination pagination) {
        Integer pageSize = pagination != null ? pagination.pageSize() : null;
        String pageToken = pagination != null ? pagination.pageToken() : null;
        return GoogleGenAiBatchUtils.listBatchJobs(client, pageSize, pageToken, this::processResponse);
    }

    /**
     * Creates and enqueues a batch of image generation requests for asynchronous processing.
     *
     * @param displayName a user-defined name for the batch
     * @param prompts     a list of image generation prompt strings to be processed in the batch
     * @return a {@link BatchResponse} representing the initial state of the batch operation
     */
    public BatchResponse<Response<Image>> submit(String displayName, List<String> prompts) {
        List<InlinedRequest> inlinedRequests = prompts.stream()
                .map(prompt -> createInlinedRequest(new ImageGenerationRequest(prompt)))
                .collect(Collectors.toList());

        BatchJobSource src =
                BatchJobSource.builder().inlinedRequests(inlinedRequests).build();

        CreateBatchJobConfig config =
                CreateBatchJobConfig.builder().displayName(displayName).build();

        BatchJob batchJob = withRetryMappingExceptions(() -> client.batches.create(modelName, src, config), maxRetries);
        return processResponse(batchJob);
    }

    /**
     * Creates a batch of image generation requests from an uploaded file.
     *
     * @param displayName a user-defined name for the batch
     * @param file        the Google GenAI File object representing the uploaded file containing batch requests
     * @return a {@link BatchResponse} representing the initial state of the batch operation
     */
    public BatchResponse<Response<Image>> submit(String displayName, File file) {
        BatchJobSource src = BatchJobSource.builder()
                .fileName(file.name().isPresent() ? file.name().get() : null)
                .build();

        CreateBatchJobConfig config =
                CreateBatchJobConfig.builder().displayName(displayName).build();

        BatchJob batchJob = withRetryMappingExceptions(() -> client.batches.create(modelName, src, config), maxRetries);
        return processResponse(batchJob);
    }

    /**
     * Deletes a batch job from the system.
     */
    public void deleteBatchJob(String batchId) {
        client.batches.delete(batchId, DeleteBatchJobConfig.builder().build());
    }

    private InlinedRequest createInlinedRequest(ImageGenerationRequest request) {
        Content content = Content.builder()
                .parts(List.of(Part.fromText(request.prompt())))
                .build();

        GenerateContentConfig.Builder configBuilder =
                GenerateContentConfig.builder().responseModalities(List.of("IMAGE"));

        if (safetySettings != null && !safetySettings.isEmpty()) {
            configBuilder.safetySettings(safetySettings);
        }

        if (aspectRatio != null || imageSize != null || personGeneration != null) {
            ImageConfig.Builder imageConfigBuilder = ImageConfig.builder();
            if (aspectRatio != null) {
                imageConfigBuilder.aspectRatio(aspectRatio);
            }
            if (imageSize != null) {
                imageConfigBuilder.imageSize(imageSize);
            }
            if (personGeneration != null) {
                imageConfigBuilder.personGeneration(personGeneration);
            }
            configBuilder.imageConfig(imageConfigBuilder.build());
        }

        if (labels != null && !labels.isEmpty()) {
            configBuilder.labels(labels);
        }

        return InlinedRequest.builder()
                .contents(List.of(content))
                .config(configBuilder.build())
                .build();
    }

    private BatchResponse<Response<Image>> processResponse(BatchJob batchJob) {
        String jobName = batchJob.name().orElse("unknown");
        Known state = batchJob.state().map(JobState::knownEnum).orElse(Known.JOB_STATE_UNSPECIFIED);

        BatchState translatedState = GoogleGenAiBatchUtils.toBatchState(state);

        BatchResponse.Builder<Response<Image>> builder =
                BatchResponse.<Response<Image>>builder().batchId(jobName).state(translatedState);

        if (state == Known.JOB_STATE_SUCCEEDED) {
            List<BatchItemResult<Response<Image>>> results = new ArrayList<>();
            if (batchJob.dest().isPresent()
                    && batchJob.dest().get().inlinedResponses().isPresent()) {
                var inlinedResponses = batchJob.dest().get().inlinedResponses().get();
                for (var inlined : inlinedResponses) {
                    if (inlined.response().isPresent()) {
                        var response = inlined.response().get();
                        boolean imageAdded = false;
                        if (response.parts() != null && !response.parts().isEmpty()) {
                            for (Part part : response.parts()) {
                                if (part.inlineData().isPresent()) {
                                    var blob = part.inlineData().get();
                                    if (blob.data().isPresent()) {
                                        byte[] bytes = blob.data().get();
                                        String base64Data = Base64.getEncoder().encodeToString(bytes);
                                        String mimeType = blob.mimeType().orElse("image/png");

                                        Image image = Image.builder()
                                                .base64Data(base64Data)
                                                .mimeType(mimeType)
                                                .build();
                                        results.add(BatchItemResult.success(Response.from(image)));
                                        imageAdded = true;
                                        break; // Process one image per response for now
                                    }
                                }
                            }
                        }
                        if (!imageAdded) {
                            results.add(BatchItemResult.failure(
                                    new BatchError(0, "No image data found in response", new ArrayList<>())));
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

    public record ImageGenerationRequest(String prompt) {
        public ImageGenerationRequest {
            ensureNotBlank(prompt, "prompt");
        }
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
        private String aspectRatio;
        private String imageSize;
        private String personGeneration;
        private List<SafetySetting> safetySettings;
        private String apiEndpoint;
        private Map<String, String> customHeaders;
        private Map<String, String> labels;

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
            this.modelName = modelName;
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

        public Builder aspectRatio(String aspectRatio) {
            this.aspectRatio = aspectRatio;
            return this;
        }

        public Builder imageSize(String imageSize) {
            this.imageSize = imageSize;
            return this;
        }

        public Builder personGeneration(String personGeneration) {
            this.personGeneration = personGeneration;
            return this;
        }

        public Builder safetySettings(List<SafetySetting> safetySettings) {
            this.safetySettings = safetySettings;
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

        public Builder labels(Map<String, String> labels) {
            this.labels = labels;
            return this;
        }

        public GoogleGenAiBatchImageModel build() {
            return new GoogleGenAiBatchImageModel(this);
        }
    }
}
