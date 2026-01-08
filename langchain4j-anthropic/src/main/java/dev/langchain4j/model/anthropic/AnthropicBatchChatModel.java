package dev.langchain4j.model.anthropic;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.firstNotNull;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.anthropic.AnthropicChatModel.toThinking;
import static dev.langchain4j.model.anthropic.InternalAnthropicHelper.createAnthropicRequest;
import static dev.langchain4j.model.anthropic.InternalAnthropicHelper.validate;
import static dev.langchain4j.model.anthropic.internal.api.AnthropicCacheType.EPHEMERAL;
import static dev.langchain4j.model.anthropic.internal.api.AnthropicCacheType.NO_CACHE;
import static dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper.toAiMessage;
import static dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper.toFinishReason;
import static dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper.toTokenUsage;

import dev.langchain4j.Experimental;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.anthropic.AnthropicBatchResponse.AnthropicBatchError;
import dev.langchain4j.model.anthropic.AnthropicBatchResponse.AnthropicBatchIncomplete;
import dev.langchain4j.model.anthropic.AnthropicBatchResponse.AnthropicBatchSuccess;
import dev.langchain4j.model.anthropic.internal.api.AnthropicBatchIndividualResponse;
import dev.langchain4j.model.anthropic.internal.api.AnthropicBatchListResponse;
import dev.langchain4j.model.anthropic.internal.api.AnthropicBatchRequest;
import dev.langchain4j.model.anthropic.internal.api.AnthropicBatchRequestCounts;
import dev.langchain4j.model.anthropic.internal.api.AnthropicBatchResponse;
import dev.langchain4j.model.anthropic.internal.api.AnthropicBatchResult;
import dev.langchain4j.model.anthropic.internal.api.AnthropicBatchResult.AnthropicBatchResultCanceled;
import dev.langchain4j.model.anthropic.internal.api.AnthropicBatchResult.AnthropicBatchResultError;
import dev.langchain4j.model.anthropic.internal.api.AnthropicBatchResult.AnthropicBatchResultExpired;
import dev.langchain4j.model.anthropic.internal.api.AnthropicBatchResult.AnthropicBatchResultSuccess;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCreateBatchRequest;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCreateMessageResponse;
import dev.langchain4j.model.anthropic.internal.client.AnthropicClient;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Anthropic Batch Chat Model for asynchronous processing of large volumes of chat requests.
 *
 * <p>The Message Batches API allows you to submit multiple requests together for asynchronous
 * processing, with 50% cost savings compared to standard API prices. Most batches complete
 * within 1 hour, though processing can take up to 24 hours.</p>
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>50% cost reduction on all token usage</li>
 *   <li>Process up to 100,000 requests or 256 MB per batch</li>
 *   <li>Support for all Messages API features (vision, tools, system messages, etc.)</li>
 *   <li>Results available for 29 days after batch creation</li>
 * </ul>
 *
 * <h2>Limitations</h2>
 * <ul>
 *   <li>No streaming support for batch requests</li>
 *   <li>Batches may take up to 24 hours to process</li>
 *   <li>Results may not be returned in the same order as requests</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * AnthropicBatchChatModel batchModel = AnthropicBatchChatModel.builder()
 *     .apiKey(System.getenv("ANTHROPIC_API_KEY"))
 *     .modelName("claude-sonnet-4-5")
 *     .build();
 *
 * List<ChatRequest> requests = List.of(
 *     ChatRequest.builder().messages(UserMessage.from("Hello")).build(),
 *     ChatRequest.builder().messages(UserMessage.from("How are you?")).build()
 * );
 *
 * AnthropicBatchResponse<ChatResponse> batch = batchModel.createBatchInline(requests);
 *
 * // Poll for completion
 * while (batch instanceof AnthropicBatchIncomplete) {
 *     Thread.sleep(60000);
 *     batch = batchModel.retrieveBatchResults(((AnthropicBatchIncomplete<?>) batch).name());
 * }
 *
 * if (batch instanceof AnthropicBatchSuccess<ChatResponse> success) {
 *     for (var result : success.results()) {
 *         if (result.isSucceeded()) {
 *             System.out.println(result.result().aiMessage().text());
 *         }
 *     }
 * }
 * }</pre>
 *
 * @see <a href="https://docs.anthropic.com/en/docs/build-with-claude/batch-processing">Anthropic Batch Processing</a>
 */
@Experimental
public class AnthropicBatchChatModel {
    private final AnthropicClient client;
    private final boolean cacheSystemMessages;
    private final boolean cacheTools;
    private final String thinkingType;
    private final Integer thinkingBudgetTokens;
    private final boolean returnThinking;
    private final boolean sendThinking;
    private final ChatRequestParameters defaultRequestParameters;
    private final String toolChoiceName;
    private final Boolean disableParallelToolUse;
    private final List<AnthropicServerTool> serverTools;
    private final boolean returnServerToolResults;
    private final Set<String> toolMetadataKeysToSend;
    private final String userId;
    private final java.util.Map<String, Object> customParameters;
    private final Boolean strictTools;

    /**
     * Constructs a new {@link AnthropicBatchChatModel} using the provided builder configuration.
     *
     * @param builder the builder containing configuration parameters
     */
    public AnthropicBatchChatModel(Builder builder) {
        this(
                builder,
                AnthropicClient.builder()
                        .httpClientBuilder(builder.httpClientBuilder)
                        .baseUrl(getOrDefault(builder.baseUrl, "https://api.anthropic.com/v1/"))
                        .apiKey(builder.apiKey)
                        .version(getOrDefault(builder.version, "2023-06-01"))
                        .beta(builder.beta)
                        .timeout(builder.timeout)
                        .logRequests(getOrDefault(builder.logRequests, false))
                        .logResponses(getOrDefault(builder.logResponses, false))
                        .logger(builder.logger)
                        .build());
    }

    AnthropicBatchChatModel(Builder builder, AnthropicClient client) {
        this.client = client;
        this.cacheSystemMessages = getOrDefault(builder.cacheSystemMessages, false);
        this.cacheTools = getOrDefault(builder.cacheTools, false);
        this.thinkingType = builder.thinkingType;
        this.thinkingBudgetTokens = builder.thinkingBudgetTokens;
        this.returnThinking = getOrDefault(builder.returnThinking, false);
        this.sendThinking = getOrDefault(builder.sendThinking, true);
        this.toolChoiceName = builder.toolChoiceName;
        this.disableParallelToolUse = builder.disableParallelToolUse;
        this.serverTools = copy(builder.serverTools);
        this.returnServerToolResults = getOrDefault(builder.returnServerToolResults, false);
        this.toolMetadataKeysToSend = copy(builder.toolMetadataKeysToSend);
        this.userId = builder.userId;
        this.customParameters = copy(builder.customParameters);
        this.strictTools = builder.strictTools;

        ChatRequestParameters commonParameters;
        if (builder.defaultRequestParameters != null) {
            validate(builder.defaultRequestParameters);
            commonParameters = builder.defaultRequestParameters;
        } else {
            commonParameters = DefaultChatRequestParameters.EMPTY;
        }

        this.defaultRequestParameters = DefaultChatRequestParameters.builder()
                .modelName(getOrDefault(builder.modelName, commonParameters.modelName()))
                .temperature(getOrDefault(builder.temperature, commonParameters.temperature()))
                .topP(getOrDefault(builder.topP, commonParameters.topP()))
                .topK(getOrDefault(builder.topK, commonParameters.topK()))
                .maxOutputTokens(
                        getOrDefault(builder.maxTokens, getOrDefault(commonParameters.maxOutputTokens(), 1024)))
                .stopSequences(getOrDefault(builder.stopSequences, commonParameters.stopSequences()))
                .toolSpecifications(getOrDefault(builder.toolSpecifications, commonParameters.toolSpecifications()))
                .toolChoice(getOrDefault(builder.toolChoice, commonParameters.toolChoice()))
                .responseFormat(getOrDefault(builder.responseFormat, commonParameters.responseFormat()))
                .build();
    }

    /**
     * Creates a new builder for constructing an {@link AnthropicBatchChatModel}.
     *
     * @return a new {@link Builder} instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a new Message Batch with the provided chat requests.
     *
     * <p>Each request is assigned a unique custom ID (UUID) for tracking results.</p>
     *
     * @param requests the list of chat requests to process
     * @return the batch response containing the batch ID and initial status
     */
    public dev.langchain4j.model.anthropic.AnthropicBatchResponse<ChatResponse> createBatchInline(
            List<ChatRequest> requests) {
        return createBatchInline(null, requests);
    }

    /**
     * Creates a new Message Batch with the provided chat requests.
     *
     * <p>Each request is assigned a unique custom ID (UUID) for tracking results.
     *
     * @param customId optional custom id for the batch (currently unused by API)
     * @param requests the list of chat requests to process
     * @return the batch response containing the batch ID and initial status
     */
    public dev.langchain4j.model.anthropic.AnthropicBatchResponse<ChatResponse> createBatchInline(
            @Nullable String customId, List<ChatRequest> requests) {

        var batchBuilder = AnthropicCreateBatchRequest.builder();
        for (ChatRequest chatRequest : requests) {
            var mergedRequest = ChatRequest.builder()
                    .messages(chatRequest.messages())
                    .parameters(defaultRequestParameters.overrideWith(chatRequest.parameters()))
                    .build();
            validate(mergedRequest.parameters());

            batchBuilder.addRequest(AnthropicBatchRequest.builder()
                    .customId(
                            firstNotNull("customId", customId, UUID.randomUUID().toString()))
                    .params(createAnthropicRequest(
                            mergedRequest,
                            toThinking(thinkingType, thinkingBudgetTokens),
                            sendThinking,
                            cacheSystemMessages ? EPHEMERAL : NO_CACHE,
                            cacheTools ? EPHEMERAL : NO_CACHE,
                            false, // streaming not supported for batch
                            toolChoiceName,
                            disableParallelToolUse,
                            serverTools,
                            toolMetadataKeysToSend,
                            userId,
                            customParameters,
                            strictTools))
                    .build());
        }

        AnthropicBatchResponse response = client.createBatch(batchBuilder.build());
        return toBatchResponse(response);
    }

    /**
     * Retrieves the current status and results of a Message Batch.
     *
     * <p>If the batch has completed processing, results will be fetched and included
     * in the response. If still processing, returns an {@link AnthropicBatchIncomplete}.</p>
     *
     * @param name the batch identifier
     * @return the current batch status, potentially with results if completed
     */
    public dev.langchain4j.model.anthropic.AnthropicBatchResponse<ChatResponse> retrieveBatchResults(
            AnthropicBatchName name) {
        var response = client.retrieveBatch(name.id());

        if (response.isEnded() && response.resultsUrl() != null) {
            List<AnthropicBatchIndividualResponse> rawResults = client.retrieveBatchResults(name.id());
            return toBatchSuccessWithResults(response, rawResults);
        }

        return toBatchResponse(response);
    }

    /**
     * Cancels a Message Batch that is currently processing.
     *
     * <p>After cancellation is initiated, the batch will transition to "canceling" status
     * and eventually to "ended". Partial results may be available for requests that
     * were processed before cancellation.</p>
     *
     * @param name the batch identifier
     */
    public void cancelBatchJob(AnthropicBatchName name) {
        client.cancelBatch(name.id());
    }

    /**
     * Deletes a Message Batch.
     *
     * <p><strong>Note:</strong> The Anthropic API does not currently support deleting batches.
     * This method is provided for API compatibility but will throw an exception.</p>
     *
     * @param name the batch identifier
     * @throws UnsupportedOperationException always, as batch deletion is not supported
     */
    public void deleteBatchJob(AnthropicBatchName name) {
        throw new UnsupportedOperationException("Anthropic API does not support deleting batches");
    }

    /**
     * Lists all Message Batches in the workspace.
     *
     * @param pageSize  maximum number of batches to return per page (optional)
     * @param pageToken token for fetching the next page (use {@link AnthropicBatchList#nextPageToken()})
     * @return paginated list of batches
     */
    public AnthropicBatchList<ChatResponse> listBatchJobs(@Nullable Integer pageSize, @Nullable String pageToken) {
        AnthropicBatchListResponse response = client.listBatches(pageSize, pageToken);

        List<dev.langchain4j.model.anthropic.AnthropicBatchResponse<ChatResponse>> batches = new ArrayList<>();
        for (AnthropicBatchResponse batch : response.data()) {
            batches.add(toBatchResponse(batch));
        }

        return new AnthropicBatchList<>(batches, Boolean.TRUE.equals(response.hasMore()), response.lastId());
    }

    private dev.langchain4j.model.anthropic.AnthropicBatchResponse<ChatResponse> toBatchResponse(
            AnthropicBatchResponse response) {

        if (response.isEnded()) {
            var counts = response.requestCounts();

            if (counts != null && counts.succeeded() == 0 && counts.errored() > 0) {
                return new AnthropicBatchError<>(
                        AnthropicBatchName.of(response.id()),
                        "All requests in batch failed",
                        response.createdAt(),
                        response.endedAt());
            }
            // Ended but no results fetched yet - return incomplete to signal need to fetch results
            if (response.resultsUrl() == null) {
                return new AnthropicBatchIncomplete<>(
                        AnthropicBatchName.of(response.id()),
                        response.processingStatus(),
                        0,
                        getCountOrDefault(counts, AnthropicBatchRequestCounts::succeeded),
                        getCountOrDefault(counts, AnthropicBatchRequestCounts::errored),
                        getCountOrDefault(counts, AnthropicBatchRequestCounts::canceled),
                        getCountOrDefault(counts, AnthropicBatchRequestCounts::expired),
                        response.createdAt(),
                        response.expiresAt());
            }
        }

        return new AnthropicBatchIncomplete<>(
                AnthropicBatchName.of(response.id()),
                response.processingStatus(),
                getCountOrDefault(response.requestCounts(), AnthropicBatchRequestCounts::processing),
                getCountOrDefault(response.requestCounts(), AnthropicBatchRequestCounts::succeeded),
                getCountOrDefault(response.requestCounts(), AnthropicBatchRequestCounts::errored),
                getCountOrDefault(response.requestCounts(), AnthropicBatchRequestCounts::canceled),
                getCountOrDefault(response.requestCounts(), AnthropicBatchRequestCounts::expired),
                response.createdAt(),
                response.expiresAt());
    }

    private int getCountOrDefault(
            AnthropicBatchRequestCounts counts,
            java.util.function.Function<AnthropicBatchRequestCounts, Integer> countFunction) {
        return counts != null ? getOrDefault(countFunction.apply(counts), 0) : 0;
    }

    private dev.langchain4j.model.anthropic.AnthropicBatchResponse<ChatResponse> toBatchSuccessWithResults(
            AnthropicBatchResponse response, List<AnthropicBatchIndividualResponse> rawResults) {
        return new AnthropicBatchSuccess<>(
                AnthropicBatchName.of(response.id()),
                rawResults.stream().map(this::convertResult).toList(),
                getCountOrDefault(response.requestCounts(), AnthropicBatchRequestCounts::succeeded),
                getCountOrDefault(response.requestCounts(), AnthropicBatchRequestCounts::errored),
                getCountOrDefault(response.requestCounts(), AnthropicBatchRequestCounts::canceled),
                getCountOrDefault(response.requestCounts(), AnthropicBatchRequestCounts::expired),
                response.createdAt(),
                response.endedAt());
    }

    private AnthropicBatchIndividualResult<ChatResponse> convertResult(AnthropicBatchIndividualResponse rawResult) {
        AnthropicBatchResult result = rawResult.result();

        if (result instanceof AnthropicBatchResultSuccess success) {
            AnthropicCreateMessageResponse messageResponse = success.message();
            ChatResponse chatResponse = ChatResponse.builder()
                    .aiMessage(toAiMessage(messageResponse.content(), returnThinking, returnServerToolResults))
                    .metadata(AnthropicChatResponseMetadata.builder()
                            .id(messageResponse.id())
                            .modelName(messageResponse.model())
                            .tokenUsage(toTokenUsage(messageResponse.usage()))
                            .finishReason(toFinishReason(messageResponse.stopReason()))
                            .build())
                    .build();

            return AnthropicBatchIndividualResult.<ChatResponse>builder()
                    .customId(rawResult.customId())
                    .result(chatResponse)
                    .resultType("succeeded")
                    .build();
        } else if (result instanceof AnthropicBatchResultError error) {
            return AnthropicBatchIndividualResult.<ChatResponse>builder()
                    .customId(rawResult.customId())
                    .error(error.error().type() + ": " + error.error().message())
                    .resultType("errored")
                    .build();
        } else if (result instanceof AnthropicBatchResultCanceled) {
            return AnthropicBatchIndividualResult.<ChatResponse>builder()
                    .customId(rawResult.customId())
                    .error("Request was canceled before processing")
                    .resultType("canceled")
                    .build();
        } else if (result instanceof AnthropicBatchResultExpired) {
            return AnthropicBatchIndividualResult.<ChatResponse>builder()
                    .customId(rawResult.customId())
                    .error("Request expired before processing (24 hour limit)")
                    .resultType("expired")
                    .build();
        }

        throw new IllegalStateException("Unknown result type: " + result);
    }

    /**
     * Builder for constructing {@link AnthropicBatchChatModel} instances.
     */
    public static class Builder {

        private HttpClientBuilder httpClientBuilder;
        private String baseUrl;
        private String apiKey;
        private String version;
        private String beta;
        private String modelName;
        private Double temperature;
        private Double topP;
        private Integer topK;
        private Integer maxTokens;
        private List<String> stopSequences;
        private dev.langchain4j.model.chat.request.ResponseFormat responseFormat;
        private List<dev.langchain4j.agent.tool.ToolSpecification> toolSpecifications;
        private dev.langchain4j.model.chat.request.ToolChoice toolChoice;
        private String toolChoiceName;
        private Boolean disableParallelToolUse;
        private List<AnthropicServerTool> serverTools;
        private Boolean returnServerToolResults;
        private Set<String> toolMetadataKeysToSend;
        private Boolean cacheSystemMessages;
        private Boolean cacheTools;
        private String thinkingType;
        private Integer thinkingBudgetTokens;
        private Boolean returnThinking;
        private Boolean sendThinking;
        private Duration timeout;
        private Boolean logRequests;
        private Boolean logResponses;
        private Logger logger;
        private ChatRequestParameters defaultRequestParameters;
        private String userId;
        private java.util.Map<String, Object> customParameters;
        private Boolean strictTools;

        public Builder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder beta(String beta) {
            this.beta = beta;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder modelName(AnthropicChatModelName modelName) {
            this.modelName = modelName.toString();
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public Builder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder stopSequences(List<String> stopSequences) {
            this.stopSequences = stopSequences;
            return this;
        }

        public Builder responseFormat(dev.langchain4j.model.chat.request.ResponseFormat responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        public Builder toolSpecifications(List<dev.langchain4j.agent.tool.ToolSpecification> toolSpecifications) {
            this.toolSpecifications = toolSpecifications;
            return this;
        }

        public Builder toolChoice(dev.langchain4j.model.chat.request.ToolChoice toolChoice) {
            this.toolChoice = toolChoice;
            return this;
        }

        public Builder toolChoiceName(String toolChoiceName) {
            this.toolChoiceName = toolChoiceName;
            return this;
        }

        public Builder disableParallelToolUse(Boolean disableParallelToolUse) {
            this.disableParallelToolUse = disableParallelToolUse;
            return this;
        }

        public Builder serverTools(List<AnthropicServerTool> serverTools) {
            this.serverTools = serverTools;
            return this;
        }

        public Builder returnServerToolResults(Boolean returnServerToolResults) {
            this.returnServerToolResults = returnServerToolResults;
            return this;
        }

        public Builder toolMetadataKeysToSend(Set<String> toolMetadataKeysToSend) {
            this.toolMetadataKeysToSend = toolMetadataKeysToSend;
            return this;
        }

        public Builder cacheSystemMessages(Boolean cacheSystemMessages) {
            this.cacheSystemMessages = cacheSystemMessages;
            return this;
        }

        public Builder cacheTools(Boolean cacheTools) {
            this.cacheTools = cacheTools;
            return this;
        }

        public Builder thinkingType(String thinkingType) {
            this.thinkingType = thinkingType;
            return this;
        }

        public Builder thinkingBudgetTokens(Integer thinkingBudgetTokens) {
            this.thinkingBudgetTokens = thinkingBudgetTokens;
            return this;
        }

        public Builder returnThinking(Boolean returnThinking) {
            this.returnThinking = returnThinking;
            return this;
        }

        public Builder sendThinking(Boolean sendThinking) {
            this.sendThinking = sendThinking;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public Builder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public Builder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public Builder defaultRequestParameters(ChatRequestParameters defaultRequestParameters) {
            this.defaultRequestParameters = defaultRequestParameters;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder customParameters(java.util.Map<String, Object> customParameters) {
            this.customParameters = customParameters;
            return this;
        }

        public Builder strictTools(Boolean strictTools) {
            this.strictTools = strictTools;
            return this;
        }

        public AnthropicBatchChatModel build() {
            return new AnthropicBatchChatModel(this);
        }
    }
}
