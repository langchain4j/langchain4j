package dev.langchain4j.model.google.genai;

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
import dev.langchain4j.model.google.genai.GoogleGenAiBatchRequestResponse.BatchError;
import dev.langchain4j.model.google.genai.GoogleGenAiBatchRequestResponse.BatchIncomplete;
import dev.langchain4j.model.google.genai.GoogleGenAiBatchRequestResponse.BatchJobState;
import dev.langchain4j.model.google.genai.GoogleGenAiBatchRequestResponse.BatchList;
import dev.langchain4j.model.google.genai.GoogleGenAiBatchRequestResponse.BatchName;
import dev.langchain4j.model.google.genai.GoogleGenAiBatchRequestResponse.BatchResponse;
import dev.langchain4j.model.google.genai.GoogleGenAiBatchRequestResponse.BatchSuccess;
import dev.langchain4j.model.google.genai.GoogleGenAiBatchRequestResponse.Status;
import dev.langchain4j.model.output.Response;
import java.time.Duration;
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
public final class GoogleGenAiBatchImageModel {

    private final Client client;
    private final String modelName;

    private final List<SafetySetting> safetySettings;
    private final String aspectRatio;
    private final String imageSize;
    private final String personGeneration;
    private final Map<String, String> labels;

    private GoogleGenAiBatchImageModel(Builder builder) {
        this.modelName = builder.modelName;
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

    /**
     * Creates and enqueues a batch of image generation requests for asynchronous processing.
     */
    public BatchResponse<Response<Image>> createBatchInline(
            String displayName, Long priority, List<ImageGenerationRequest> requests) {

        List<InlinedRequest> inlinedRequests =
                requests.stream().map(this::createInlinedRequest).collect(Collectors.toList());

        BatchJobSource src =
                BatchJobSource.builder().inlinedRequests(inlinedRequests).build();

        CreateBatchJobConfig config =
                CreateBatchJobConfig.builder().displayName(displayName).build();

        BatchJob batchJob = client.batches.create(modelName, src, config);
        return processResponse(batchJob);
    }

    /**
     * Creates a batch of image generation requests from an uploaded file.
     */
    public BatchResponse<Response<Image>> createBatchFromFile(String displayName, File file) {
        BatchJobSource src = BatchJobSource.builder()
                .fileName(file.name().isPresent() ? file.name().get() : null)
                .build();

        CreateBatchJobConfig config =
                CreateBatchJobConfig.builder().displayName(displayName).build();

        BatchJob batchJob = client.batches.create(modelName, src, config);
        return processResponse(batchJob);
    }

    /**
     * Retrieves the current state and results of a batch operation.
     */
    public BatchResponse<Response<Image>> retrieveBatchResults(BatchName name) {
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
    public BatchList<Response<Image>> listBatchJobs(Integer pageSize, String pageToken) {
        return GoogleGenAiBatchRequestResponse.listBatchJobs(client, pageSize, pageToken, this::processResponse);
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

        BatchJobState translatedState;
        try {
            translatedState = BatchJobState.valueOf(state.name());
        } catch (IllegalArgumentException e) {
            translatedState = BatchJobState.UNRECOGNIZED;
        }

        if (state == Known.JOB_STATE_SUCCEEDED) {
            List<Response<Image>> responses = new ArrayList<>();
            List<Status> errors = new ArrayList<>();

            if (batchJob.dest().isPresent()
                    && batchJob.dest().get().inlinedResponses().isPresent()) {
                var inlinedResponses = batchJob.dest().get().inlinedResponses().get();
                for (var inlined : inlinedResponses) {
                    if (inlined.response().isPresent()) {
                        var response = inlined.response().get();

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
                                        responses.add(Response.from(image));
                                        break; // Process one image per response for now
                                    }
                                }
                            }
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
