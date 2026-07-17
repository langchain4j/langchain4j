package dev.langchain4j.model.mistralai;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.mistralai.InternalMistralAIHelper.createMistralAiRequest;
import static dev.langchain4j.model.mistralai.InternalMistralAIHelper.validate;
import static dev.langchain4j.model.mistralai.internal.mapper.MistralAiMapper.aiMessageFrom;
import static dev.langchain4j.model.mistralai.internal.mapper.MistralAiMapper.finishReasonFrom;
import static dev.langchain4j.model.mistralai.internal.mapper.MistralAiMapper.tokenUsageFrom;

import dev.langchain4j.Experimental;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.batch.BatchError;
import dev.langchain4j.model.batch.BatchItemResult;
import dev.langchain4j.model.batch.BatchPage;
import dev.langchain4j.model.batch.BatchPagination;
import dev.langchain4j.model.batch.BatchRequest;
import dev.langchain4j.model.batch.BatchResponse;
import dev.langchain4j.model.batch.BatchState;
import dev.langchain4j.model.chat.BatchChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.mistralai.internal.api.MistralAiBatchJob;
import dev.langchain4j.model.mistralai.internal.api.MistralAiBatchJobRequest;
import dev.langchain4j.model.mistralai.internal.api.MistralAiBatchJobsResponse;
import dev.langchain4j.model.mistralai.internal.api.MistralAiBatchResultEntry;
import dev.langchain4j.model.mistralai.internal.api.MistralAiChatCompletionRequest;
import dev.langchain4j.model.mistralai.internal.api.MistralAiChatCompletionResponse;
import dev.langchain4j.model.mistralai.internal.client.MistralAiClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * A {@link BatchChatModel} for the Mistral
 * <a href="https://docs.mistral.ai/capabilities/batch/">Batch API</a>, which processes multiple chat
 * requests asynchronously at 50% of the standard per-token price.
 *
 * <p>Requests are submitted inline: each {@link ChatRequest} is mapped to a request identical to the one
 * {@link MistralAiChatModel} would send, so per-request parameters (temperature, tools, response format, etc.)
 * behave the same in a batch as in a single call. The returned {@link BatchResponse} carries the batch id and
 * its current {@link BatchState}; results are fetched by {@link #retrieve(String)} once the job has produced
 * them, preserving submission order.</p>
 *
 * <p>All requests in a batch run against the single model configured on this batch model
 * (via {@link Builder#modelName(String)}), matching the Mistral constraint of one model per job.</p>
 *
 * @see BatchChatModel
 * @see BatchResponse
 */
@Experimental
public final class MistralAiBatchChatModel implements BatchChatModel {

    private static final String CHAT_COMPLETIONS_ENDPOINT = "/v1/chat/completions";
    private static final String CUSTOM_ID_PREFIX = "request-";

    private static final String STATUS_QUEUED = "QUEUED";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_TIMEOUT_EXCEEDED = "TIMEOUT_EXCEEDED";
    private static final String STATUS_CANCELLATION_REQUESTED = "CANCELLATION_REQUESTED";
    private static final String STATUS_CANCELLED = "CANCELLED";

    private final MistralAiClient client;
    private final ChatRequestParameters defaultRequestParameters;
    private final Boolean safePrompt;
    private final Integer randomSeed;
    private final boolean sendThinking;
    private final boolean returnThinking;
    private final boolean strictJsonSchema;
    private final int maxRetries;
    private final Integer timeoutHours;

    public MistralAiBatchChatModel(Builder builder) {
        this.client = MistralAiClient.builder()
                .httpClientBuilder(builder.httpClientBuilder)
                .baseUrl(getOrDefault(builder.baseUrl, "https://api.mistral.ai/v1"))
                .apiKey(builder.apiKey)
                .timeout(builder.timeout)
                .logRequests(getOrDefault(builder.logRequests, false))
                .logResponses(getOrDefault(builder.logResponses, false))
                .logger(builder.logger)
                .customHeaders(builder.customHeadersSupplier)
                .build();
        this.safePrompt = builder.safePrompt;
        this.randomSeed = builder.randomSeed;
        this.sendThinking = getOrDefault(builder.sendThinking, false);
        this.returnThinking = getOrDefault(builder.returnThinking, false);
        this.strictJsonSchema = getOrDefault(builder.strictJsonSchema, false);
        this.maxRetries = getOrDefault(builder.maxRetries, 2);
        this.timeoutHours = builder.timeoutHours;
        this.defaultRequestParameters = initDefaultRequestParameters(builder);
    }

    private static ChatRequestParameters initDefaultRequestParameters(Builder builder) {
        ChatRequestParameters commonParameters;
        if (builder.defaultRequestParameters != null) {
            validate(builder.defaultRequestParameters);
            commonParameters = builder.defaultRequestParameters;
        } else {
            commonParameters = DefaultChatRequestParameters.EMPTY;
        }
        return DefaultChatRequestParameters.builder()
                .modelName(getOrDefault(builder.modelName, commonParameters.modelName()))
                .temperature(getOrDefault(builder.temperature, commonParameters.temperature()))
                .topP(getOrDefault(builder.topP, commonParameters.topP()))
                .frequencyPenalty(getOrDefault(builder.frequencyPenalty, commonParameters.frequencyPenalty()))
                .presencePenalty(getOrDefault(builder.presencePenalty, commonParameters.presencePenalty()))
                .maxOutputTokens(getOrDefault(builder.maxTokens, commonParameters.maxOutputTokens()))
                .stopSequences(getOrDefault(builder.stopSequences, commonParameters.stopSequences()))
                .toolSpecifications(commonParameters.toolSpecifications())
                .toolChoice(commonParameters.toolChoice())
                .responseFormat(getOrDefault(builder.responseFormat, commonParameters.responseFormat()))
                .build();
    }

    @Override
    public BatchResponse<ChatResponse> submit(BatchRequest<ChatRequest> request) {
        List<ChatRequest> requests = request.requests();
        List<MistralAiBatchJobRequest.Request> items = new ArrayList<>();
        for (int i = 0; i < requests.size(); i++) {
            items.add(new MistralAiBatchJobRequest.Request(CUSTOM_ID_PREFIX + i, toMistralAiRequest(requests.get(i))));
        }
        MistralAiBatchJobRequest jobRequest = MistralAiBatchJobRequest.builder()
                .requests(items)
                .endpoint(CHAT_COMPLETIONS_ENDPOINT)
                .model(defaultRequestParameters.modelName())
                .timeoutHours(timeoutHours)
                .build();
        MistralAiBatchJob job = withRetryMappingExceptions(() -> client.createBatchJob(jobRequest), maxRetries);
        return toBatchResponse(job, List.of());
    }

    @Override
    public BatchResponse<ChatResponse> retrieve(String batchId) {
        MistralAiBatchJob job = withRetryMappingExceptions(() -> client.retrieveBatchJob(batchId), maxRetries);
        List<BatchItemResult<ChatResponse>> results = List.of();
        if (job.outputFile != null || job.errorFile != null) {
            List<MistralAiBatchResultEntry> entries = new ArrayList<>();
            if (job.outputFile != null) {
                entries.addAll(
                        withRetryMappingExceptions(() -> client.downloadBatchResults(job.outputFile), maxRetries));
            }
            if (job.errorFile != null) {
                entries.addAll(
                        withRetryMappingExceptions(() -> client.downloadBatchResults(job.errorFile), maxRetries));
            }
            entries.sort(Comparator.comparingInt(this::customIdIndex));
            results = entries.stream().map(this::toBatchItemResult).toList();
        }
        return toBatchResponse(job, results);
    }

    @Override
    public void cancel(String batchId) {
        withRetryMappingExceptions(() -> client.cancelBatchJob(batchId), maxRetries);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The Mistral Batch API paginates by zero-based page index. When more pages remain, the returned
     * {@link BatchPage#nextPageToken()} is the next page index (as a string) to pass back via
     * {@link BatchPagination#pageToken()}.</p>
     */
    @Override
    public BatchPage<ChatResponse> list(@Nullable BatchPagination pagination) {
        Integer pageSize = pagination != null ? pagination.pageSize() : null;
        Integer page =
                pagination != null && pagination.pageToken() != null ? Integer.parseInt(pagination.pageToken()) : null;
        MistralAiBatchJobsResponse response =
                withRetryMappingExceptions(() -> client.listBatchJobs(page, pageSize), maxRetries);
        List<BatchResponse<ChatResponse>> batches = new ArrayList<>();
        if (response.data != null) {
            for (MistralAiBatchJob job : response.data) {
                batches.add(toBatchResponse(job, List.of()));
            }
        }
        int currentPage = page != null ? page : 0;
        String nextPageToken = null;
        if (pageSize != null
                && pageSize > 0
                && response.total != null
                && (long) (currentPage + 1) * pageSize < response.total) {
            nextPageToken = String.valueOf(currentPage + 1);
        }
        return new BatchPage<>(batches, nextPageToken);
    }

    public static Builder builder() {
        return new Builder();
    }

    private MistralAiChatCompletionRequest toMistralAiRequest(ChatRequest chatRequest) {
        ChatRequestParameters merged = defaultRequestParameters.overrideWith(chatRequest.parameters());
        ChatRequest effectiveRequest = ChatRequest.builder()
                .messages(chatRequest.messages())
                .parameters(merged)
                .build();
        return createMistralAiRequest(effectiveRequest, safePrompt, randomSeed, false, sendThinking, strictJsonSchema);
    }

    private ChatResponse toChatResponse(MistralAiChatCompletionResponse response) {
        return ChatResponse.builder()
                .aiMessage(aiMessageFrom(response, returnThinking))
                .metadata(MistralAiChatResponseMetadata.builder()
                        .id(response.getId())
                        .modelName(response.getModel())
                        .tokenUsage(tokenUsageFrom(response.getUsage()))
                        .finishReason(
                                finishReasonFrom(response.getChoices().get(0).getFinishReason()))
                        .build())
                .build();
    }

    private BatchResponse<ChatResponse> toBatchResponse(
            MistralAiBatchJob job, List<BatchItemResult<ChatResponse>> results) {
        return BatchResponse.<ChatResponse>builder()
                .batchId(job.id)
                .state(toBatchState(job.status))
                .results(results)
                .build();
    }

    private static BatchState toBatchState(@Nullable String status) {
        if (status == null) {
            return BatchState.UNSPECIFIED;
        }
        return switch (status) {
            case STATUS_QUEUED -> BatchState.PENDING;
            case STATUS_RUNNING, STATUS_CANCELLATION_REQUESTED -> BatchState.RUNNING;
            case STATUS_SUCCESS -> BatchState.SUCCEEDED;
            case STATUS_FAILED -> BatchState.FAILED;
            case STATUS_TIMEOUT_EXCEEDED -> BatchState.EXPIRED;
            case STATUS_CANCELLED -> BatchState.CANCELLED;
            default -> BatchState.UNSPECIFIED;
        };
    }

    private BatchItemResult<ChatResponse> toBatchItemResult(MistralAiBatchResultEntry entry) {
        MistralAiBatchResultEntry.Response response = entry.response;
        boolean succeeded = entry.error == null
                && response != null
                && response.body != null
                && (response.statusCode == null || response.statusCode < 400)
                && hasChoices(response.body);
        if (succeeded) {
            return BatchItemResult.success(toChatResponse(response.body));
        }
        int code = response != null && response.statusCode != null ? response.statusCode : 0;
        List<Map<String, Object>> details = entry.error != null ? List.of(entry.error) : null;
        return BatchItemResult.failure(new BatchError(code, errorMessage(entry), details));
    }

    private static boolean hasChoices(MistralAiChatCompletionResponse body) {
        return body.getChoices() != null && !body.getChoices().isEmpty();
    }

    private static String errorMessage(MistralAiBatchResultEntry entry) {
        if (entry.error != null) {
            Object message = entry.error.get("message");
            return message != null ? message.toString() : entry.error.toString();
        }
        if (entry.response != null && entry.response.statusCode != null && entry.response.statusCode >= 400) {
            return "Request failed with status code " + entry.response.statusCode;
        }
        return "Malformed batch result: no response body or choices";
    }

    private int customIdIndex(MistralAiBatchResultEntry entry) {
        String customId = entry.customId;
        if (customId != null && customId.startsWith(CUSTOM_ID_PREFIX)) {
            try {
                return Integer.parseInt(customId.substring(CUSTOM_ID_PREFIX.length()));
            } catch (NumberFormatException ignored) {
                // custom id not produced by this model; keep it last rather than failing
            }
        }
        return Integer.MAX_VALUE;
    }

    public static final class Builder {

        private HttpClientBuilder httpClientBuilder;
        private String baseUrl;
        private String apiKey;
        private String modelName;
        private Double temperature;
        private Double topP;
        private Integer maxTokens;
        private Double frequencyPenalty;
        private Double presencePenalty;
        private List<String> stopSequences;
        private ResponseFormat responseFormat;
        private Boolean safePrompt;
        private Integer randomSeed;
        private Boolean sendThinking;
        private Boolean returnThinking;
        private Boolean strictJsonSchema;
        private Integer timeoutHours;
        private Duration timeout;
        private Integer maxRetries;
        private Boolean logRequests;
        private Boolean logResponses;
        private Logger logger;
        private Supplier<Map<String, String>> customHeadersSupplier;
        private ChatRequestParameters defaultRequestParameters;

        private Builder() {}

        /**
         * Sets a custom {@link HttpClientBuilder} for the underlying HTTP client.
         *
         * @return {@code this}
         */
        public Builder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        /**
         * Sets the base URL of the Mistral API. Defaults to {@code https://api.mistral.ai/v1}.
         *
         * @return {@code this}
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * Sets the Mistral API key used to authenticate requests.
         *
         * @return {@code this}
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * Sets the model that every request in the batch runs against (one model per job).
         *
         * @return {@code this}
         */
        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /**
         * @return {@code this}
         */
        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        /**
         * @return {@code this}
         */
        public Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        /**
         * @return {@code this}
         */
        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        /**
         * @return {@code this}
         */
        public Builder frequencyPenalty(Double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
            return this;
        }

        /**
         * @return {@code this}
         */
        public Builder presencePenalty(Double presencePenalty) {
            this.presencePenalty = presencePenalty;
            return this;
        }

        /**
         * @return {@code this}
         */
        public Builder stopSequences(List<String> stopSequences) {
            this.stopSequences = stopSequences;
            return this;
        }

        /**
         * @return {@code this}
         */
        public Builder responseFormat(ResponseFormat responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        /**
         * Injects a safety prompt in front of all conversations when {@code true}.
         *
         * @return {@code this}
         */
        public Builder safePrompt(Boolean safePrompt) {
            this.safePrompt = safePrompt;
            return this;
        }

        /**
         * Sets the seed for deterministic sampling.
         *
         * @return {@code this}
         */
        public Builder randomSeed(Integer randomSeed) {
            this.randomSeed = randomSeed;
            return this;
        }

        /**
         * @return {@code this}
         */
        public Builder sendThinking(Boolean sendThinking) {
            this.sendThinking = sendThinking;
            return this;
        }

        /**
         * @return {@code this}
         */
        public Builder returnThinking(Boolean returnThinking) {
            this.returnThinking = returnThinking;
            return this;
        }

        /**
         * @return {@code this}
         */
        public Builder strictJsonSchema(Boolean strictJsonSchema) {
            this.strictJsonSchema = strictJsonSchema;
            return this;
        }

        /**
         * Sets the job timeout in hours, after which unprocessed requests expire.
         *
         * @return {@code this}
         */
        public Builder timeoutHours(Integer timeoutHours) {
            this.timeoutHours = timeoutHours;
            return this;
        }

        /**
         * Sets the HTTP request timeout for calls to the Mistral API.
         *
         * @return {@code this}
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Sets the number of times to retry a request on transient errors. Defaults to {@code 2}.
         *
         * @return {@code this}
         */
        public Builder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * @return {@code this}
         */
        public Builder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        /**
         * @return {@code this}
         */
        public Builder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        /**
         * @return {@code this}
         */
        public Builder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        /**
         * @return {@code this}
         */
        public Builder customHeaders(Map<String, String> customHeaders) {
            this.customHeadersSupplier = () -> customHeaders;
            return this;
        }

        /**
         * @return {@code this}
         */
        public Builder customHeaders(Supplier<Map<String, String>> customHeadersSupplier) {
            this.customHeadersSupplier = customHeadersSupplier;
            return this;
        }

        /**
         * Sets common default {@link ChatRequestParameters}; explicit builder setters take precedence over these.
         *
         * @return {@code this}
         */
        public Builder defaultRequestParameters(ChatRequestParameters defaultRequestParameters) {
            this.defaultRequestParameters = defaultRequestParameters;
            return this;
        }

        public MistralAiBatchChatModel build() {
            return new MistralAiBatchChatModel(this);
        }
    }
}
