package dev.langchain4j.model.google.genai;

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
import com.google.genai.types.InlinedRequest;
import com.google.genai.types.JobState;
import com.google.genai.types.JobState.Known;
import com.google.genai.types.SafetySetting;
import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.google.genai.GoogleGenAiBatchRequestResponse.BatchError;
import dev.langchain4j.model.google.genai.GoogleGenAiBatchRequestResponse.BatchIncomplete;
import dev.langchain4j.model.google.genai.GoogleGenAiBatchRequestResponse.BatchJobState;
import dev.langchain4j.model.google.genai.GoogleGenAiBatchRequestResponse.BatchList;
import dev.langchain4j.model.google.genai.GoogleGenAiBatchRequestResponse.BatchName;
import dev.langchain4j.model.google.genai.GoogleGenAiBatchRequestResponse.BatchResponse;
import dev.langchain4j.model.google.genai.GoogleGenAiBatchRequestResponse.BatchSuccess;
import dev.langchain4j.model.google.genai.GoogleGenAiBatchRequestResponse.Status;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Provides an interface for interacting with the Google GenAI Batch API for Chat models.
 */
@Experimental
public final class GoogleGenAiBatchChatModel {

    private final Client client;
    private final String modelName;

    // Configuration parameters reused from the chat model builder
    private final List<SafetySetting> safetySettings;
    private final Integer thinkingBudget;
    private final Integer seed;
    private final boolean googleSearchEnabled;
    private final boolean googleMapsEnabled;
    private final boolean urlContextEnabled;
    private final List<String> allowedFunctionNames;
    private final String vertexSearchDatastore;
    private final Map<String, String> labels;
    private final ChatRequestParameters defaultRequestParameters;

    private GoogleGenAiBatchChatModel(Builder builder) {
        this.modelName = builder.modelName;
        this.safetySettings = builder.safetySettings != null ? new ArrayList<>(builder.safetySettings) : null;
        this.thinkingBudget = builder.thinkingBudget;
        this.seed = builder.seed;
        this.googleSearchEnabled = builder.googleSearch != null ? builder.googleSearch : false;
        this.googleMapsEnabled = builder.googleMaps != null ? builder.googleMaps : false;
        this.urlContextEnabled = builder.urlContext != null ? builder.urlContext : false;
        this.allowedFunctionNames =
                builder.allowedFunctionNames != null ? new ArrayList<>(builder.allowedFunctionNames) : null;
        this.vertexSearchDatastore = builder.vertexSearchDatastore;
        this.labels = builder.labels != null ? new HashMap<>(builder.labels) : null;
        this.defaultRequestParameters = builder.defaultRequestParameters;

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
     * Creates and enqueues a batch of content generation requests for asynchronous processing.
     * All requests must use the same model.
     *
     * @param displayName a user-defined name for the batch
     * @param priority    not explicitly supported in google-genai Java SDK BatchJob creation, ignored.
     * @param requests    a list of chat requests to be processed in the batch
     * @return a {@link BatchResponse} representing the initial state of the batch operation
     */
    public BatchResponse<ChatResponse> createBatchInline(
            String displayName, Long priority, List<ChatRequest> requests) {
        validateModelInChatRequests(modelName, requests);

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
     * Creates a batch of chat requests from an uploaded file.
     *
     * @param displayName a user-defined name for the batch
     * @param file        the Google GenAI File object representing the uploaded file containing batch requests
     * @return a {@link BatchResponse} representing the initial state of the batch operation
     */
    public BatchResponse<ChatResponse> createBatchFromFile(String displayName, File file) {
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
    public BatchResponse<ChatResponse> retrieveBatchResults(BatchName name) {
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
    public BatchList<ChatResponse> listBatchJobs(Integer pageSize, String pageToken) {
        // Native listing does not support pagination directly via parameters matching this exactly in a single call
        // easily?
        // Let's use the pager mechanism.
        // For simplicity, we just use the list() method and convert.
        throw new UnsupportedOperationException("Batch List requires Pager API adaptation. Not fully supported yet.");
    }

    private InlinedRequest createInlinedRequest(ChatRequest request) {
        Content systemInstruction = GoogleGenAiContentMapper.toSystemInstruction(request.messages());
        List<Content> contents = GoogleGenAiContentMapper.toContents(request.messages());

        ChatRequestParameters params = defaultRequestParameters != null
                ? defaultRequestParameters.overrideWith(request.parameters())
                : request.parameters();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                params,
                systemInstruction,
                safetySettings,
                thinkingBudget,
                seed,
                googleSearchEnabled,
                googleMapsEnabled,
                urlContextEnabled,
                allowedFunctionNames,
                vertexSearchDatastore,
                labels);

        return InlinedRequest.builder().contents(contents).config(config).build();
    }

    private BatchResponse<ChatResponse> processResponse(BatchJob batchJob) {
        String jobName = batchJob.name().orElse("unknown");
        Known state = batchJob.state().map(JobState::knownEnum).orElse(Known.JOB_STATE_UNSPECIFIED);

        BatchJobState translatedState;
        try {
            translatedState = BatchJobState.valueOf(state.name());
        } catch (IllegalArgumentException e) {
            translatedState = BatchJobState.UNRECOGNIZED;
        }

        if (state == Known.JOB_STATE_SUCCEEDED) {
            List<ChatResponse> responses = new ArrayList<>();
            List<Status> errors = new ArrayList<>();

            if (batchJob.dest().isPresent()
                    && batchJob.dest().get().inlinedResponses().isPresent()) {
                var inlinedResponses = batchJob.dest().get().inlinedResponses().get();
                for (var inlined : inlinedResponses) {
                    if (inlined.response().isPresent()) {
                        responses.add(GoogleGenAiContentMapper.toChatResponse(
                                inlined.response().get(), batchJob.model().orElse(modelName)));
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

    public static class Builder {
        private Client client;
        private GoogleCredentials googleCredentials;
        private String apiKey;
        private String projectId;
        private String location;
        private String modelName;
        private Duration timeout;
        private Integer thinkingBudget;
        private Integer seed;
        private Boolean googleSearch;
        private Boolean googleMaps;
        private Boolean urlContext;
        private List<SafetySetting> safetySettings;
        private List<String> allowedFunctionNames;
        private ChatRequestParameters defaultRequestParameters;
        private String vertexSearchDatastore;
        private Map<String, String> labels;
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
            this.modelName = modelName;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder thinkingBudget(Integer thinkingBudget) {
            this.thinkingBudget = thinkingBudget;
            return this;
        }

        public Builder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        public Builder googleSearch(Boolean googleSearch) {
            this.googleSearch = googleSearch;
            return this;
        }

        public Builder googleMaps(Boolean googleMaps) {
            this.googleMaps = googleMaps;
            return this;
        }

        public Builder urlContext(Boolean urlContext) {
            this.urlContext = urlContext;
            return this;
        }

        public Builder safetySettings(List<SafetySetting> safetySettings) {
            this.safetySettings = safetySettings;
            return this;
        }

        public Builder allowedFunctionNames(List<String> allowedFunctionNames) {
            this.allowedFunctionNames = allowedFunctionNames;
            return this;
        }

        public Builder defaultRequestParameters(ChatRequestParameters defaultRequestParameters) {
            this.defaultRequestParameters = defaultRequestParameters;
            return this;
        }

        public Builder vertexSearchDatastore(String vertexSearchDatastore) {
            this.vertexSearchDatastore = vertexSearchDatastore;
            return this;
        }

        public Builder labels(Map<String, String> labels) {
            this.labels = labels;
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

        public GoogleGenAiBatchChatModel build() {
            return new GoogleGenAiBatchChatModel(this);
        }
    }
}
