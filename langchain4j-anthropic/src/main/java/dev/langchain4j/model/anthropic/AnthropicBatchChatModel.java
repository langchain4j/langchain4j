package dev.langchain4j.model.anthropic;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper.toAiMessage;
import static dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper.toFinishReason;
import static dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper.toTokenUsage;

import dev.langchain4j.Experimental;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.anthropic.internal.api.AnthropicBatch;
import dev.langchain4j.model.anthropic.internal.api.AnthropicBatchResult;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCacheType;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCreateBatchRequest;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCreateMessageRequest;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCreateMessageResponse;
import dev.langchain4j.model.anthropic.internal.api.AnthropicError;
import dev.langchain4j.model.anthropic.internal.api.AnthropicListBatchesResponse;
import dev.langchain4j.model.anthropic.internal.client.AnthropicClient;
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
import dev.langchain4j.model.chat.response.ChatResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * A {@link BatchChatModel} for the Anthropic
 * <a href="https://docs.anthropic.com/en/api/creating-message-batches">Message Batches API</a>, which processes
 * multiple chat requests asynchronously at 50% of the standard per-token price.
 *
 * <p>Each submitted {@link ChatRequest} is mapped to a request identical to the one
 * {@link AnthropicChatModel} would send, so per-request parameters (model, tools, thinking, caching, etc.) behave
 * the same in a batch as in a single call. Requests are submitted inline; the returned {@link BatchResponse} carries
 * the batch id and its current {@link BatchState}. Results are fetched by {@link #retrieve(String)} once the batch has
 * ended, preserving submission order.</p>
 *
 * @see BatchChatModel
 * @see BatchResponse
 */
@Experimental
public final class AnthropicBatchChatModel implements BatchChatModel {

    private static final String CUSTOM_ID_PREFIX = "request-";

    private static final String STATUS_IN_PROGRESS = "in_progress";
    private static final String STATUS_CANCELING = "canceling";
    private static final String STATUS_ENDED = "ended";
    private static final String RESULT_SUCCEEDED = "succeeded";

    private final AnthropicClient client;
    private final AnthropicChatRequestParameters defaultRequestParameters;
    private final int maxRetries;

    public AnthropicBatchChatModel(Builder builder) {
        this.client = AnthropicClient.builder()
                .httpClientBuilder(builder.httpClientBuilder)
                .baseUrl(getOrDefault(builder.baseUrl, "https://api.anthropic.com/v1/"))
                .apiKey(builder.apiKey)
                .version(getOrDefault(builder.version, AnthropicChatModel.ANTHROPIC_VERSION))
                .beta(builder.beta)
                .timeout(builder.timeout)
                .logRequests(getOrDefault(builder.logRequests, false))
                .logResponses(getOrDefault(builder.logResponses, false))
                .build();
        this.maxRetries = getOrDefault(builder.maxRetries, 2);
        this.defaultRequestParameters = AnthropicChatRequestParameters.builder()
                .modelName(builder.modelName)
                .maxOutputTokens(getOrDefault(builder.maxTokens, 1024))
                .temperature(builder.temperature)
                .topP(builder.topP)
                .topK(builder.topK)
                .stopSequences(builder.stopSequences)
                .build();
    }

    @Override
    public BatchResponse<ChatResponse> submit(BatchRequest<ChatRequest> request) {
        List<ChatRequest> requests = request.requests();
        List<AnthropicCreateBatchRequest.Request> items = new ArrayList<>();
        for (int i = 0; i < requests.size(); i++) {
            items.add(
                    new AnthropicCreateBatchRequest.Request(CUSTOM_ID_PREFIX + i, toAnthropicRequest(requests.get(i))));
        }
        AnthropicCreateBatchRequest batchRequest = new AnthropicCreateBatchRequest(items);
        AnthropicBatch batch = withRetryMappingExceptions(() -> client.createBatch(batchRequest), maxRetries);
        return toBatchResponse(batch, List.of());
    }

    @Override
    public BatchResponse<ChatResponse> retrieve(String batchId) {
        AnthropicBatch batch = withRetryMappingExceptions(() -> client.retrieveBatch(batchId), maxRetries);
        List<BatchItemResult<ChatResponse>> results = List.of();
        if (STATUS_ENDED.equals(batch.processingStatus)) {
            List<AnthropicBatchResult> rawResults =
                    withRetryMappingExceptions(() -> client.retrieveBatchResults(batchId), maxRetries);
            rawResults.sort(Comparator.comparingInt(this::customIdIndex));
            results = rawResults.stream().map(this::toBatchItemResult).toList();
        }
        return toBatchResponse(batch, results);
    }

    @Override
    public void cancel(String batchId) {
        withRetryMappingExceptions(() -> client.cancelBatch(batchId), maxRetries);
    }

    @Override
    public BatchPage<ChatResponse> list(@Nullable BatchPagination pagination) {
        Integer pageSize = pagination != null ? pagination.pageSize() : null;
        String pageToken = pagination != null ? pagination.pageToken() : null;
        AnthropicListBatchesResponse response =
                withRetryMappingExceptions(() -> client.listBatches(pageSize, pageToken), maxRetries);
        List<BatchResponse<ChatResponse>> batches = new ArrayList<>();
        if (response.data != null) {
            for (AnthropicBatch batch : response.data) {
                batches.add(toBatchResponse(batch, List.of()));
            }
        }
        String nextPageToken = response.hasMore ? response.lastId : null;
        return new BatchPage<>(batches, nextPageToken);
    }

    /**
     * @return a new {@link Builder} for {@link AnthropicBatchChatModel}.
     */
    public static Builder builder() {
        return new Builder();
    }

    private AnthropicCreateMessageRequest toAnthropicRequest(ChatRequest chatRequest) {
        ChatRequestParameters merged = defaultRequestParameters.overrideWith(chatRequest.parameters());
        AnthropicChatRequestParameters parameters = (AnthropicChatRequestParameters) merged;
        ChatRequest effectiveRequest = ChatRequest.builder()
                .messages(chatRequest.messages())
                .parameters(merged)
                .build();
        return InternalAnthropicHelper.createAnthropicRequest(
                effectiveRequest,
                AnthropicChatModel.toThinking(parameters.thinkingType(), parameters.thinkingBudgetTokens(), null),
                getOrDefault(parameters.sendThinking(), true),
                getOrDefault(parameters.midConversationSystemMessages(), false),
                getOrDefault(parameters.cacheSystemMessages(), false)
                        ? AnthropicCacheType.EPHEMERAL
                        : AnthropicCacheType.NO_CACHE,
                getOrDefault(parameters.cacheTools(), false)
                        ? AnthropicCacheType.EPHEMERAL
                        : AnthropicCacheType.NO_CACHE,
                false,
                parameters.toolChoiceName(),
                parameters.disableParallelToolUse(),
                List.of(),
                Set.of(),
                parameters.userId(),
                List.of(),
                null,
                null,
                getOrDefault(parameters.returnCacheDiagnostics(), false),
                parameters.previousMessageId());
    }

    private ChatResponse toChatResponse(AnthropicCreateMessageResponse response) {
        AnthropicChatResponseMetadata metadata = AnthropicChatResponseMetadata.builder()
                .id(response.id)
                .modelName(response.model)
                .tokenUsage(toTokenUsage(response.usage))
                .finishReason(toFinishReason(response.stopReason))
                .build();
        return ChatResponse.builder()
                .aiMessage(toAiMessage(response.content, false, false))
                .metadata(metadata)
                .build();
    }

    private BatchResponse<ChatResponse> toBatchResponse(
            AnthropicBatch batch, List<BatchItemResult<ChatResponse>> results) {
        return BatchResponse.<ChatResponse>builder()
                .batchId(batch.id)
                .state(toBatchState(batch.processingStatus))
                .results(results)
                .build();
    }

    private static BatchState toBatchState(@Nullable String processingStatus) {
        if (processingStatus == null) {
            return BatchState.UNSPECIFIED;
        }
        return switch (processingStatus) {
            case STATUS_IN_PROGRESS, STATUS_CANCELING -> BatchState.RUNNING;
            case STATUS_ENDED -> BatchState.SUCCEEDED;
            default -> BatchState.UNSPECIFIED;
        };
    }

    private BatchItemResult<ChatResponse> toBatchItemResult(AnthropicBatchResult result) {
        AnthropicBatchResult.Result outcome = result.result;
        if (outcome != null && RESULT_SUCCEEDED.equals(outcome.type) && outcome.message != null) {
            return BatchItemResult.success(toChatResponse(outcome.message));
        }
        return BatchItemResult.failure(toBatchError(outcome));
    }

    private static BatchError toBatchError(AnthropicBatchResult.@Nullable Result outcome) {
        if (outcome == null) {
            return new BatchError(0, "unknown", null);
        }
        if (outcome.error != null && outcome.error.error != null) {
            AnthropicError error = outcome.error.error;
            List<Map<String, Object>> details = error.type != null ? List.of(Map.of("type", error.type)) : null;
            String message = error.message != null ? error.message : outcome.type;
            return new BatchError(0, message, details);
        }
        return new BatchError(0, outcome.type, null);
    }

    private int customIdIndex(AnthropicBatchResult result) {
        String customId = result.customId;
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
        private String version;
        private String beta;
        private String modelName;
        private Integer maxTokens;
        private Double temperature;
        private Double topP;
        private Integer topK;
        private List<String> stopSequences;
        private Duration timeout;
        private Integer maxRetries;
        private Boolean logRequests;
        private Boolean logResponses;

        private Builder() {}

        /**
         * Sets a custom {@link HttpClientBuilder} for the underlying HTTP client.
         * Use this to configure timeouts, proxies, or other HTTP-level settings.
         *
         * @param httpClientBuilder the HTTP client builder
         * @return {@code this}
         */
        public Builder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        /**
         * Sets the base URL of the Anthropic API.
         * <p>
         * Defaults to {@code https://api.anthropic.com/v1/}.
         *
         * @param baseUrl the base URL
         * @return {@code this}
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * Sets the Anthropic API key used to authenticate requests.
         *
         * @param apiKey the API key
         * @return {@code this}
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * Sets the value of the {@code anthropic-version} request header.
         * <p>
         * Defaults to {@code 2023-06-01}.
         * See the <a href="https://docs.anthropic.com/en/api/versioning">Anthropic API versioning docs</a>.
         *
         * @param version the API version string
         * @return {@code this}
         */
        public Builder version(String version) {
            this.version = version;
            return this;
        }

        /**
         * Sets the value of the {@code anthropic-beta} request header to opt into beta features.
         * <p>
         * See the <a href="https://docs.anthropic.com/en/api/beta-headers">Anthropic beta headers docs</a>.
         *
         * @param beta the beta feature identifier
         * @return {@code this}
         */
        public Builder beta(String beta) {
            this.beta = beta;
            return this;
        }

        /**
         * Sets the model applied to every batched request, specified as a string model ID.
         * <p>
         * See {@link AnthropicChatModelName} for available model constants. It can be overridden per request
         * via {@link ChatRequestParameters#modelName()}.
         *
         * @param modelName the model ID, e.g. {@code "claude-opus-4-5"}
         * @return {@code this}
         */
        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /**
         * Sets the maximum number of tokens to generate per batched request.
         * <p>
         * Defaults to {@code 1024} if not set.
         *
         * @param maxTokens the maximum number of output tokens
         * @return {@code this}
         */
        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        /**
         * Sets the sampling temperature in the range {@code [0.0, 1.0]}.
         * Higher values produce more random output; lower values produce more deterministic output.
         * <p>
         * Cannot be used together with {@link #topP(Double)}.
         *
         * @param temperature the sampling temperature
         * @return {@code this}
         */
        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        /**
         * Sets the nucleus sampling probability (top-p) in the range {@code (0.0, 1.0]}.
         * Only the tokens whose cumulative probability exceeds this threshold are considered.
         * <p>
         * Cannot be used together with {@link #temperature(Double)}.
         *
         * @param topP the nucleus sampling threshold
         * @return {@code this}
         */
        public Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        /**
         * Sets the top-K sampling value. Only the {@code topK} most-likely next tokens are considered at each step.
         * <p>
         * Recommended for advanced use only; {@link #temperature(Double)} is usually sufficient.
         *
         * @param topK the number of top tokens to sample from
         * @return {@code this}
         */
        public Builder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        /**
         * Sets sequences that, when generated, will cause the model to stop generating further tokens.
         *
         * @param stopSequences the list of stop sequences
         * @return {@code this}
         */
        public Builder stopSequences(List<String> stopSequences) {
            this.stopSequences = stopSequences;
            return this;
        }

        /**
         * Sets the HTTP request timeout for calls to the Anthropic API.
         *
         * @param timeout the request timeout
         * @return {@code this}
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Sets the number of times to retry a request on transient errors (e.g. rate limits, server errors).
         * <p>
         * Defaults to {@code 2}.
         *
         * @param maxRetries the maximum number of retry attempts
         * @return {@code this}
         */
        public Builder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * Enables debug logging of HTTP request bodies sent to the Anthropic API.
         *
         * @param logRequests whether to log requests
         * @return {@code this}
         */
        public Builder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        /**
         * Enables debug logging of HTTP response bodies received from the Anthropic API.
         *
         * @param logResponses whether to log responses
         * @return {@code this}
         */
        public Builder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        /**
         * @return a new {@link AnthropicBatchChatModel} configured with this builder's values.
         */
        public AnthropicBatchChatModel build() {
            return new AnthropicBatchChatModel(this);
        }
    }
}
