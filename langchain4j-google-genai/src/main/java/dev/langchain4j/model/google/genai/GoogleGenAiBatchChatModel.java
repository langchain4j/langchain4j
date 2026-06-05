package dev.langchain4j.model.google.genai;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.copy;
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
import com.google.genai.types.InlinedRequest;
import com.google.genai.types.JobState;
import com.google.genai.types.JobState.Known;
import com.google.genai.types.SafetySetting;
import dev.langchain4j.Experimental;
import dev.langchain4j.model.batch.BatchItemResult;
import dev.langchain4j.model.batch.BatchPage;
import dev.langchain4j.model.batch.BatchPagination;
import dev.langchain4j.model.batch.BatchRequest;
import dev.langchain4j.model.batch.BatchResponse;
import dev.langchain4j.model.batch.BatchState;
import dev.langchain4j.model.chat.BatchChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Provides an interface for interacting with the Google GenAI Batch API for
 * Chat models.
 */
@Experimental
public final class GoogleGenAiBatchChatModel implements BatchChatModel {

    private final Client client;
    private final String modelName;
    private final Integer maxRetries;

    // Configuration parameters reused from the chat model builder
    private final List<SafetySetting> safetySettings;
    private final Integer thinkingBudget;
    private final String thinkingLevel;
    private final Integer seed;
    private final boolean googleSearchEnabled;
    private final boolean googleMapsEnabled;
    private final boolean urlContextEnabled;
    private final List<String> allowedFunctionNames;
    private final String vertexSearchDatastore;
    private final Map<String, String> labels;
    private final String cachedContent;
    private final ChatRequestParameters defaultRequestParameters;

    private GoogleGenAiBatchChatModel(Builder builder) {
        this.modelName = ensureNotBlank(builder.modelName, "modelName");
        this.maxRetries = getOrDefault(builder.maxRetries, 3);
        this.safetySettings = copy(builder.safetySettings);
        this.thinkingBudget = builder.thinkingBudget;
        this.thinkingLevel = builder.thinkingLevel;
        this.seed = builder.seed;
        this.googleSearchEnabled = getOrDefault(builder.googleSearch, false);
        this.googleMapsEnabled = getOrDefault(builder.googleMaps, false);
        this.urlContextEnabled = getOrDefault(builder.urlContext, false);
        this.allowedFunctionNames = copy(builder.allowedFunctionNames);
        this.vertexSearchDatastore = builder.vertexSearchDatastore;
        this.labels = builder.labels != null ? new HashMap<>(builder.labels) : null;
        this.cachedContent = builder.cachedContent;
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

    @Override
    public BatchResponse<ChatResponse> submit(BatchRequest<ChatRequest> request) {
        String timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss")
                .withZone(ZoneId.systemDefault())
                .format(Instant.now());
        return submit("batch-chat-job-" + timestamp, request.requests());
    }

    @Override
    public BatchResponse<ChatResponse> retrieve(String batchId) {
        BatchJob batchJob =
                client.batches.get(batchId, GetBatchJobConfig.builder().build());
        return processResponse(batchJob);
    }

    @Override
    public void cancel(String batchId) {
        client.batches.cancel(batchId, CancelBatchJobConfig.builder().build());
    }

    @Override
    public BatchPage<ChatResponse> list(BatchPagination pagination) {
        Integer pageSize = pagination != null ? pagination.pageSize() : null;
        String pageToken = pagination != null ? pagination.pageToken() : null;
        return GoogleGenAiBatchUtils.listBatchJobs(client, pageSize, pageToken, this::processResponse);
    }

    /**
     * Creates and enqueues a batch of content generation requests for asynchronous
     * processing.
     * All requests must use the same model.
     *
     * @param displayName a user-defined name for the batch
     * @param requests    a list of chat requests to be processed in the batch
     * @return a {@link BatchResponse} representing the initial state of the batch
     *         operation
     */
    public BatchResponse<ChatResponse> submit(String displayName, List<ChatRequest> requests) {
        validateModelInChatRequests(modelName, requests);

        List<InlinedRequest> inlinedRequests =
                requests.stream().map(this::createInlinedRequest).collect(Collectors.toList());

        BatchJobSource src =
                BatchJobSource.builder().inlinedRequests(inlinedRequests).build();

        CreateBatchJobConfig config =
                CreateBatchJobConfig.builder().displayName(displayName).build();

        BatchJob batchJob = withRetryMappingExceptions(() -> client.batches.create(modelName, src, config), maxRetries);
        return processResponse(batchJob);
    }

    /**
     * Creates a batch of chat requests from an uploaded file.
     *
     * @param displayName a user-defined name for the batch
     * @param file        the Google GenAI File object representing the uploaded
     *                    file containing batch requests
     * @return a {@link BatchResponse} representing the initial state of the batch
     *         operation
     */
    public BatchResponse<ChatResponse> submit(String displayName, File file) {
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
                thinkingLevel,
                seed,
                googleSearchEnabled,
                googleMapsEnabled,
                urlContextEnabled,
                allowedFunctionNames,
                vertexSearchDatastore,
                labels,
                cachedContent);

        return InlinedRequest.builder().contents(contents).config(config).build();
    }

    private BatchResponse<ChatResponse> processResponse(BatchJob batchJob) {
        String jobName = batchJob.name().orElse("unknown");
        Known state = batchJob.state().map(JobState::knownEnum).orElse(Known.JOB_STATE_UNSPECIFIED);

        BatchState translatedState = GoogleGenAiBatchUtils.toBatchState(state);

        BatchResponse.Builder<ChatResponse> builder =
                BatchResponse.<ChatResponse>builder().batchId(jobName).state(translatedState);

        if (state == Known.JOB_STATE_SUCCEEDED) {
            List<BatchItemResult<ChatResponse>> results = new ArrayList<>();
            if (batchJob.dest().isPresent()
                    && batchJob.dest().get().inlinedResponses().isPresent()) {
                var inlinedResponses = batchJob.dest().get().inlinedResponses().get();
                for (var inlined : inlinedResponses) {
                    if (inlined.response().isPresent()) {
                        results.add(BatchItemResult.success(GoogleGenAiContentMapper.toChatResponse(
                                inlined.response().get(), batchJob.model().orElse(modelName))));
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
        private Integer maxRetries;
        private Duration timeout;
        private Integer thinkingBudget;
        private String thinkingLevel;
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
        private String cachedContent;

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

        /**
         * The thinking budget to use. This is a legacy parameter. For Gemini 3.x
         * models, use {@link #thinkingLevel(String)} instead.
         */
        public Builder thinkingBudget(Integer thinkingBudget) {
            this.thinkingBudget = thinkingBudget;
            return this;
        }

        /**
         * The thinking level to use. This is the recommended parameter for Gemini 3.x
         * models.
         * Allowed values are {@code "MINIMAL"}, {@code "LOW"}, {@code "MEDIUM"},
         * {@code "HIGH"}.
         * Note that this cannot be used together with {@link #thinkingBudget(Integer)}.
         */
        public Builder thinkingLevel(String thinkingLevel) {
            this.thinkingLevel = thinkingLevel;
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

        public Builder cachedContent(String cachedContent) {
            this.cachedContent = cachedContent;
            return this;
        }

        public GoogleGenAiBatchChatModel build() {
            return new GoogleGenAiBatchChatModel(this);
        }
    }
}
