package dev.langchain4j.model.openai;

import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onCompleteResponse;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onCompleteToolCall;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onPartialResponse;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onPartialThinking;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onPartialToolCall;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onUnmappedRawEvent;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.withLoggingExceptions;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNotNullOrEmpty;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.ModelProvider.OPEN_AI;
import static dev.langchain4j.model.openai.internal.OpenAiUtils.DEFAULT_OPENAI_URL;
import static dev.langchain4j.model.openai.internal.OpenAiUtils.DEFAULT_USER_AGENT;
import static dev.langchain4j.model.openai.internal.OpenAiUtils.fromOpenAiResponseFormat;
import static dev.langchain4j.model.openai.internal.OpenAiUtils.toOpenAiChatRequest;
import static dev.langchain4j.model.openai.internal.OpenAiUtils.validate;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.time.Duration.ofSeconds;
import static java.util.Arrays.asList;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.internal.ExceptionMapper;
import dev.langchain4j.internal.MappingTrackingStreamingChatResponseHandler;
import dev.langchain4j.internal.ToolCallBuilder;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.internal.OpenAiClient;
import dev.langchain4j.model.openai.internal.ParsedAndRawResponse;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionChoice;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionRequest;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionResponse;
import dev.langchain4j.model.openai.internal.chat.Delta;
import dev.langchain4j.model.openai.internal.chat.ToolCall;
import dev.langchain4j.model.openai.internal.shared.StreamOptions;
import dev.langchain4j.model.openai.spi.OpenAiStreamingChatModelBuilderFactory;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;

/**
 * Represents an OpenAI language model with a chat completion interface, such as gpt-4o-mini and o3.
 * The model's response is streamed token by token and should be handled with {@link StreamingResponseHandler}.
 * You can find description of parameters <a href="https://platform.openai.com/docs/api-reference/chat/create">here</a>.
 */
public class OpenAiStreamingChatModel implements StreamingChatModel {

    private final OpenAiClient client;
    private final OpenAiChatRequestParameters defaultRequestParameters;
    private final boolean strictJsonSchema;
    private final boolean strictTools;
    private final boolean returnThinking;
    private final boolean sendThinking;
    private final String thinkingFieldName;
    private final boolean accumulateToolCallId;
    private final List<ChatModelListener> listeners;

    public OpenAiStreamingChatModel(OpenAiStreamingChatModelBuilder builder) {
        this.client = OpenAiClient.builder()
                .httpClientBuilder(builder.httpClientBuilder)
                .baseUrl(getOrDefault(builder.baseUrl, DEFAULT_OPENAI_URL))
                .apiKey(builder.apiKey)
                .organizationId(builder.organizationId)
                .projectId(builder.projectId)
                .connectTimeout(getOrDefault(builder.timeout, ofSeconds(15)))
                .readTimeout(getOrDefault(builder.timeout, ofSeconds(60)))
                .logRequests(getOrDefault(builder.logRequests, false))
                .logResponses(getOrDefault(builder.logResponses, false))
                .logger(builder.logger)
                .userAgent(DEFAULT_USER_AGENT)
                .customHeaders(builder.customHeadersSupplier)
                .customQueryParams(builder.customQueryParams)
                .build();

        ChatRequestParameters commonParameters;
        if (builder.defaultRequestParameters != null) {
            validate(builder.defaultRequestParameters);
            commonParameters = builder.defaultRequestParameters;
        } else {
            commonParameters = DefaultChatRequestParameters.EMPTY;
        }

        OpenAiChatRequestParameters openAiParameters;
        if (builder.defaultRequestParameters instanceof OpenAiChatRequestParameters openAiChatRequestParameters) {
            openAiParameters = openAiChatRequestParameters;
        } else {
            openAiParameters = OpenAiChatRequestParameters.EMPTY;
        }

        this.defaultRequestParameters = OpenAiChatRequestParameters.builder()
                // common parameters
                .modelName(getOrDefault(builder.modelName, commonParameters.modelName()))
                .temperature(getOrDefault(builder.temperature, commonParameters.temperature()))
                .topP(getOrDefault(builder.topP, commonParameters.topP()))
                .frequencyPenalty(getOrDefault(builder.frequencyPenalty, commonParameters.frequencyPenalty()))
                .presencePenalty(getOrDefault(builder.presencePenalty, commonParameters.presencePenalty()))
                .maxOutputTokens(getOrDefault(builder.maxTokens, commonParameters.maxOutputTokens()))
                .stopSequences(getOrDefault(builder.stop, commonParameters.stopSequences()))
                .toolSpecifications(commonParameters.toolSpecifications())
                .toolChoice(commonParameters.toolChoice())
                .responseFormat(getOrDefault(builder.responseFormat, commonParameters.responseFormat()))
                // OpenAI-specific parameters
                .maxCompletionTokens(getOrDefault(builder.maxCompletionTokens, openAiParameters.maxCompletionTokens()))
                .logitBias(getOrDefault(builder.logitBias, openAiParameters.logitBias()))
                .parallelToolCalls(getOrDefault(builder.parallelToolCalls, openAiParameters.parallelToolCalls()))
                .seed(getOrDefault(builder.seed, openAiParameters.seed()))
                .user(getOrDefault(builder.user, openAiParameters.user()))
                .store(getOrDefault(builder.store, openAiParameters.store()))
                .metadata(getOrDefault(builder.metadata, openAiParameters.metadata()))
                .serviceTier(getOrDefault(builder.serviceTier, openAiParameters.serviceTier()))
                .reasoningEffort(getOrDefault(builder.reasoningEffort, openAiParameters.reasoningEffort()))
                .customParameters(getOrDefault(builder.customParameters, openAiParameters.customParameters()))
                .build();
        this.strictJsonSchema = getOrDefault(builder.strictJsonSchema, false);
        this.strictTools = getOrDefault(builder.strictTools, false);
        this.returnThinking = getOrDefault(builder.returnThinking, false);
        this.sendThinking = getOrDefault(builder.sendThinking, false);
        this.thinkingFieldName = getOrDefault(builder.thinkingFieldName, "reasoning_content");
        this.accumulateToolCallId = getOrDefault(builder.accumulateToolCallId, true);
        this.listeners = copy(builder.listeners);
    }

    @Override
    public OpenAiChatRequestParameters defaultRequestParameters() {
        return defaultRequestParameters;
    }

    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {

        OpenAiChatRequestParameters parameters = (OpenAiChatRequestParameters) chatRequest.parameters();
        validate(parameters);

        ChatCompletionRequest openAiRequest =
                toOpenAiChatRequest(
                                chatRequest, parameters, sendThinking, thinkingFieldName, strictTools, strictJsonSchema)
                        .stream(true)
                        .streamOptions(
                                StreamOptions.builder().includeUsage(true).build())
                        .build();

        OpenAiStreamingResponseBuilder openAiResponseBuilder =
                new OpenAiStreamingResponseBuilder(returnThinking, accumulateToolCallId);
        ToolCallBuilder toolCallBuilder = new ToolCallBuilder();

        MappingTrackingStreamingChatResponseHandler trackingHandler =
                new MappingTrackingStreamingChatResponseHandler(handler);

        client.chatCompletion(openAiRequest)
                .onRawPartialResponse(parsedAndRawResponse -> {
                    trackingHandler.resetMappingTracking();
                    openAiResponseBuilder.append(parsedAndRawResponse);
                    handle(parsedAndRawResponse, toolCallBuilder, trackingHandler);

                    if (!trackingHandler.wasMapped()) {
                        onUnmappedRawEvent(trackingHandler, parsedAndRawResponse.rawServerSentEvent());
                    }
                })
                .onComplete(() -> {
                    if (toolCallBuilder.hasRequests()) {
                        onCompleteToolCall(trackingHandler, toolCallBuilder.buildAndReset());
                    }

                    ChatResponse completeResponse = openAiResponseBuilder.build();
                    onCompleteResponse(trackingHandler, completeResponse);
                })
                .onError(throwable -> {
                    RuntimeException mappedException = ExceptionMapper.DEFAULT.mapException(throwable);
                    withLoggingExceptions(() -> handler.onError(mappedException));
                })
                .execute();
    }

    private void handle(
            ParsedAndRawResponse<ChatCompletionResponse> parsedAndRawResponse,
            ToolCallBuilder toolCallBuilder,
            StreamingChatResponseHandler handler) {
        ChatCompletionResponse partialResponse = parsedAndRawResponse.parsedResponse();
        if (partialResponse == null) {
            return;
        }

        List<ChatCompletionChoice> choices = partialResponse.choices();
        if (isNullOrEmpty(choices)) {
            return;
        }

        ChatCompletionChoice chatCompletionChoice = choices.get(0);
        if (chatCompletionChoice == null) {
            return;
        }

        Delta delta = chatCompletionChoice.delta();
        if (delta == null) {
            return;
        }

        String content = delta.content();
        if (!isNullOrEmpty(content)) {
            onPartialResponse(handler, content, parsedAndRawResponse.streamingHandle());
        }

        String reasoningContent = delta.reasoningContent();
        if (returnThinking && !isNullOrEmpty(reasoningContent)) {
            onPartialThinking(handler, reasoningContent, parsedAndRawResponse.streamingHandle());
        }

        List<ToolCall> toolCalls = delta.toolCalls();
        if (toolCalls != null) {
            for (ToolCall toolCall : toolCalls) {

                int index;
                if (toolCall.index() != null) {
                    index = toolCall.index();
                } else {
                    index = toolCallBuilder.index();
                    // When index is null and a different tool call id appears, increment the index
                    if (toolCall.id() != null
                            && toolCallBuilder.id() != null
                            && !toolCallBuilder.id().equals(toolCall.id())) {
                        index = toolCallBuilder.index() + 1;
                    }
                }
                if (toolCallBuilder.index() != index) {
                    onCompleteToolCall(handler, toolCallBuilder.buildAndReset());
                    toolCallBuilder.updateIndex(index);
                }

                String id = toolCallBuilder.updateId(toolCall.id());
                String name = toolCallBuilder.updateName(toolCall.function().name());

                String partialArguments = toolCall.function().arguments();
                if (isNotNullOrEmpty(partialArguments)) {
                    toolCallBuilder.appendArguments(partialArguments);

                    PartialToolCall partialToolRequest = PartialToolCall.builder()
                            .index(index)
                            .id(id)
                            .name(name)
                            .partialArguments(partialArguments)
                            .build();
                    onPartialToolCall(handler, partialToolRequest, parsedAndRawResponse.streamingHandle());
                }
            }
        }
    }

    @Override
    public List<ChatModelListener> listeners() {
        return listeners;
    }

    @Override
    public ModelProvider provider() {
        return OPEN_AI;
    }

    public static OpenAiStreamingChatModelBuilder builder() {
        for (OpenAiStreamingChatModelBuilderFactory factory :
                loadFactories(OpenAiStreamingChatModelBuilderFactory.class)) {
            return factory.get();
        }
        return new OpenAiStreamingChatModelBuilder();
    }

    public static class OpenAiStreamingChatModelBuilder {

        private HttpClientBuilder httpClientBuilder;
        private String baseUrl;
        private String apiKey;
        private String organizationId;
        private String projectId;

        private ChatRequestParameters defaultRequestParameters;
        private String modelName;
        private Double temperature;
        private Double topP;
        private List<String> stop;
        private Integer maxTokens;
        private Integer maxCompletionTokens;
        private Double presencePenalty;
        private Double frequencyPenalty;
        private Map<String, Integer> logitBias;
        private ResponseFormat responseFormat;
        private Boolean strictJsonSchema;
        private Integer seed;
        private String user;
        private Boolean strictTools;
        private Boolean parallelToolCalls;
        private Boolean store;
        private Map<String, String> metadata;
        private String serviceTier;
        private String reasoningEffort;
        private Boolean returnThinking;
        private Boolean sendThinking;
        private String thinkingFieldName;
        private Boolean accumulateToolCallId;
        private Duration timeout;
        private Boolean logRequests;
        private Boolean logResponses;
        private Logger logger;
        private Supplier<Map<String, String>> customHeadersSupplier;
        private Map<String, String> customQueryParams;
        private Map<String, Object> customParameters;
        private List<ChatModelListener> listeners;

        public OpenAiStreamingChatModelBuilder() {
            // This is public so it can be extended
        }

        /**
         * Sets a custom {@link HttpClientBuilder} for the underlying HTTP client.
         * Use this to configure timeouts, proxies, or other HTTP-level settings.
         *
         * @param httpClientBuilder the HTTP client builder
         * @return {@code this}
         */
        public OpenAiStreamingChatModelBuilder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        /**
         * Sets default common {@link ChatRequestParameters} or OpenAI-specific {@link OpenAiChatRequestParameters}.
         * <br>
         * When a parameter is set via an individual builder method (e.g., {@link #modelName(String)}),
         * its value takes precedence over the same parameter set via {@link ChatRequestParameters}.
         */
        public OpenAiStreamingChatModelBuilder defaultRequestParameters(ChatRequestParameters parameters) {
            this.defaultRequestParameters = parameters;
            return this;
        }

        /**
         * Sets the model to use for streaming chat completions, specified as a string model ID.
         * <p>
         * See {@link OpenAiChatModelName} for available model constants.
         *
         * @param modelName the model ID, e.g. {@code "gpt-4o"}
         * @return {@code this}
         */
        public OpenAiStreamingChatModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /**
         * Sets the model to use for streaming chat completions using a type-safe enum constant.
         *
         * @param modelName the model name enum value
         * @return {@code this}
         */
        public OpenAiStreamingChatModelBuilder modelName(OpenAiChatModelName modelName) {
            this.modelName = modelName.toString();
            return this;
        }

        /**
         * Sets the base URL of the OpenAI-compatible API endpoint.
         * <p>
         * Defaults to {@code https://api.openai.com/v1}.
         * Override this to use OpenAI-compatible providers (e.g. Azure, Ollama, DeepSeek).
         *
         * @param baseUrl the base URL
         * @return {@code this}
         */
        public OpenAiStreamingChatModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * Sets the OpenAI API key used to authenticate requests.
         * <p>
         * Alternatively, set the {@code OPENAI_API_KEY} environment variable.
         *
         * @param apiKey the API key
         * @return {@code this}
         */
        public OpenAiStreamingChatModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * Sets the OpenAI organization ID sent in the {@code OpenAI-Organization} request header.
         * <p>
         * Required only when your API key belongs to multiple organizations.
         *
         * @param organizationId the organization ID
         * @return {@code this}
         */
        public OpenAiStreamingChatModelBuilder organizationId(String organizationId) {
            this.organizationId = organizationId;
            return this;
        }

        /**
         * Sets the OpenAI project ID sent in the {@code OpenAI-Project} request header.
         *
         * @param projectId the project ID
         * @return {@code this}
         */
        public OpenAiStreamingChatModelBuilder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        /**
         * Sets the sampling temperature in the range {@code [0.0, 2.0]}.
         * Higher values produce more random output; lower values produce more deterministic output.
         * <p>
         * Cannot be used together with {@link #topP(Double)}.
         *
         * @param temperature the sampling temperature
         * @return {@code this}
         */
        public OpenAiStreamingChatModelBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        /**
         * Sets the nucleus sampling probability (top-p).
         * Only the tokens whose cumulative probability exceeds this threshold are considered.
         * <p>
         * Cannot be used together with {@link #temperature(Double)}.
         *
         * @param topP the nucleus sampling threshold
         * @return {@code this}
         */
        public OpenAiStreamingChatModelBuilder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        /**
         * Sets sequences that, when generated, will cause the model to stop generating further tokens.
         *
         * @param stop the list of stop sequences
         * @return {@code this}
         */
        public OpenAiStreamingChatModelBuilder stop(List<String> stop) {
            this.stop = stop;
            return this;
        }

        /**
         * Sets the maximum number of tokens to generate in the response.
         * <p>
         * Prefer {@link #maxCompletionTokens(Integer)} for newer models (o-series and later).
         *
         * @param maxTokens the maximum number of output tokens
         * @return {@code this}
         */
        public OpenAiStreamingChatModelBuilder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        /**
         * Sets the maximum number of tokens that can be generated for a completion, including visible
         * output tokens and reasoning tokens.
         * <p>
         * Use this instead of {@link #maxTokens(Integer)} for o-series reasoning models.
         *
         * @param maxCompletionTokens the maximum number of completion tokens
         * @return {@code this}
         */
        public OpenAiStreamingChatModelBuilder maxCompletionTokens(Integer maxCompletionTokens) {
            this.maxCompletionTokens = maxCompletionTokens;
            return this;
        }

        /**
         * Sets the presence penalty in the range {@code [-2.0, 2.0]}.
         * Positive values penalize tokens that have already appeared, increasing the model's
         * likelihood to talk about new topics.
         *
         * @param presencePenalty the presence penalty
         * @return {@code this}
         */
        public OpenAiStreamingChatModelBuilder presencePenalty(Double presencePenalty) {
            this.presencePenalty = presencePenalty;
            return this;
        }

        /**
         * Sets the frequency penalty in the range {@code [-2.0, 2.0]}.
         * Positive values penalize tokens based on their frequency in the text so far, reducing
         * the model's likelihood to repeat the same words verbatim.
         *
         * @param frequencyPenalty the frequency penalty
         * @return {@code this}
         */
        public OpenAiStreamingChatModelBuilder frequencyPenalty(Double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
            return this;
        }

        /**
         * Sets the logit bias to adjust the likelihood of specified tokens appearing in the response.
         * Map token IDs (as strings) to bias values in the range {@code [-100, 100]}.
         *
         * @param logitBias a map of token ID to bias value
         * @return {@code this}
         */
        public OpenAiStreamingChatModelBuilder logitBias(Map<String, Integer> logitBias) {
            this.logitBias = logitBias;
            return this;
        }

        /**
         * Sets the response format, enabling structured output such as JSON mode or JSON Schema.
         *
         * @param responseFormat the desired response format
         * @return {@code this}
         */
        public OpenAiStreamingChatModelBuilder responseFormat(ResponseFormat responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        /**
         * @see #responseFormat(ResponseFormat)
         */
        public OpenAiStreamingChatModelBuilder responseFormat(String responseFormat) {
            this.responseFormat = fromOpenAiResponseFormat(responseFormat);
            return this;
        }

        /**
         * Enables strict JSON schema validation for structured outputs.
         * When {@code true}, the model strictly follows the JSON schema defined in the response format.
         * See the <a href="https://platform.openai.com/docs/guides/structured-outputs">structured outputs docs</a>.
         *
         * @param strictJsonSchema whether to enable strict JSON schema validation
         * @return {@code this}
         */
        public OpenAiStreamingChatModelBuilder strictJsonSchema(Boolean strictJsonSchema) {
            this.strictJsonSchema = strictJsonSchema;
            return this;
        }

        /**
         * Sets the random seed for deterministic sampling.
         * Requests with the same seed and parameters should return the same result.
         *
         * @param seed the random seed
         * @return {@code this}
         */
        public OpenAiStreamingChatModelBuilder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        /**
         * Sets a unique identifier representing the end-user, used by OpenAI to monitor and detect abuse.
         * See the <a href="https://platform.openai.com/docs/guides/safety-best-practices">safety best practices</a>.
         *
         * @param user the end-user identifier
         * @return {@code this}
         */
        public OpenAiStreamingChatModelBuilder user(String user) {
            this.user = user;
            return this;
        }

        /**
         * Enables strict JSON schema validation for tool input parameters.
         * When {@code true}, the model enforces the exact schema defined in tool specifications.
         *
         * @param strictTools whether to enable strict tool schema validation
         * @return {@code this}
         */
        public OpenAiStreamingChatModelBuilder strictTools(Boolean strictTools) {
            this.strictTools = strictTools;
            return this;
        }

        /**
         * Controls whether the model may call multiple tools in parallel within a single response.
         * <p>
         * Defaults to {@code true} for models that support it.
         *
         * @param parallelToolCalls whether to allow parallel tool calls
         * @return {@code this}
         */
        public OpenAiStreamingChatModelBuilder parallelToolCalls(Boolean parallelToolCalls) {
            this.parallelToolCalls = parallelToolCalls;
            return this;
        }

        /**
         * When {@code true}, stores the output of this request for use in model distillation or evals.
         * See the <a href="https://platform.openai.com/docs/api-reference/chat/create#chat-create-store">API docs</a>.
         *
         * @param store whether to store the output
         * @return {@code this}
         */
        public OpenAiStreamingChatModelBuilder store(Boolean store) {
            this.store = store;
            return this;
        }

        /**
         * Sets developer-defined metadata tags (key-value pairs) attached to the stored request.
         * Only applicable when {@link #store(Boolean)} is {@code true}.
         *
         * @param metadata the metadata map
         * @return {@code this}
         */
        public OpenAiStreamingChatModelBuilder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        /**
         * Sets the service tier for this request (e.g. {@code "auto"} or {@code "default"}).
         * See the <a href="https://platform.openai.com/docs/api-reference/chat/create#chat-create-service_tier">API docs</a>.
         *
         * @param serviceTier the service tier identifier
         * @return {@code this}
         */
        public OpenAiStreamingChatModelBuilder serviceTier(String serviceTier) {
            this.serviceTier = serviceTier;
            return this;
        }

        /**
         * Sets the reasoning effort for o-series models (e.g. {@code "low"}, {@code "medium"}, {@code "high"}).
         * Higher effort produces more thorough reasoning at the cost of more tokens and latency.
         * See the <a href="https://platform.openai.com/docs/guides/reasoning">reasoning docs</a>.
         *
         * @param reasoningEffort the reasoning effort level
         * @return {@code this}
         */
        public OpenAiStreamingChatModelBuilder reasoningEffort(String reasoningEffort) {
            this.reasoningEffort = reasoningEffort;
            return this;
        }

        /**
         * This setting is intended for <a href="https://api-docs.deepseek.com/guides/reasoning_model">DeepSeek</a>.
         * <p>
         * Controls whether to return thinking/reasoning text (if available) inside {@link AiMessage#thinking()}
         * and whether to invoke the {@link StreamingChatResponseHandler#onPartialThinking(PartialThinking)} callback.
         * Please note that this does not enable thinking/reasoning for the LLM;
         * it only controls whether to parse the {@code reasoning_content} field from the API response
         * and return it inside the {@link AiMessage}.
         * <p>
         * Disabled by default.
         * If enabled, the thinking text will be stored within the {@link AiMessage} and may be persisted.
         */
        public OpenAiStreamingChatModelBuilder returnThinking(Boolean returnThinking) {
            this.returnThinking = returnThinking;
            return this;
        }

        /**
         * This setting is intended for <a href="https://api-docs.deepseek.com/guides/reasoning_model">DeepSeek</a>.
         * <p>
         * Controls whether to include thinking/reasoning text in assistant messages when sending requests to the API.
         * This is needed for some APIs (like DeepSeek) when using reasoning mode with tool calls.
         * <p>
         * Disabled by default.
         * <p>
         * When enabled, the reasoning content from previous assistant messages (stored in {@link AiMessage#thinking()})
         * will be included in the request during message conversion to API format.
         *
         * @param sendThinking whether to send reasoning content
         * @param fieldName the field name for reasoning content
         * @return {@code this}
         */
        public OpenAiStreamingChatModelBuilder sendThinking(Boolean sendThinking, String fieldName) {
            this.sendThinking = sendThinking;
            this.thinkingFieldName = fieldName;
            return this;
        }

        /**
         * This setting is intended for <a href="https://api-docs.deepseek.com/guides/reasoning_model">DeepSeek</a>.
         * <p>
         * Controls whether to include thinking/reasoning text in assistant messages when sending requests to the API.
         * This is needed for some APIs (like DeepSeek) when using reasoning mode with tool calls.
         * Uses the default field name "reasoning_content" for the reasoning content field.
         * <p>
         * Disabled by default.
         * <p>
         * When enabled, the reasoning content from previous assistant messages (stored in {@link AiMessage#thinking()})
         * will be included in the request during message conversion to API format.
         *
         * @param sendThinking whether to send reasoning content
         * @return {@code this}
         */
        public OpenAiStreamingChatModelBuilder sendThinking(Boolean sendThinking) {
            this.sendThinking = sendThinking;
            this.thinkingFieldName = "reasoning_content";
            return this;
        }

        /**
         * Controls whether to accumulate tool call IDs in streaming responses.
         * <p>
         * This setting is useful when using OpenAI-compatible APIs (like DeepSeek or Qwen) that send
         * the complete tool call ID in every streaming chunk, rather than sending it incrementally.
         * <p>
         * Enabled by default (true) for standard OpenAI behavior.
         * Set to false for APIs like DeepSeek/Qwen that repeat the full ID in each chunk.
         * <p>
         * When enabled (true): IDs are accumulated across chunks (e.g., "abc" + "def" = "abcdef")
         * When disabled (false): Each chunk's ID replaces the previous one (e.g., "abc" -> "abc")
         *
         * @param accumulateToolCallId whether to accumulate tool call IDs
         * @return {@code this}
         */
        public OpenAiStreamingChatModelBuilder accumulateToolCallId(Boolean accumulateToolCallId) {
            this.accumulateToolCallId = accumulateToolCallId;
            return this;
        }

        /**
         * Sets the HTTP request timeout for calls to the OpenAI API.
         * <p>
         * Defaults to 15 seconds for connect and 60 seconds for read.
         *
         * @param timeout the request timeout
         * @return {@code this}
         */
        public OpenAiStreamingChatModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Enables debug logging of HTTP request bodies sent to the OpenAI API.
         *
         * @param logRequests whether to log requests
         * @return {@code this}
         */
        public OpenAiStreamingChatModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        /**
         * Enables debug logging of server-sent event response bodies received from the OpenAI API.
         *
         * @param logResponses whether to log responses
         * @return {@code this}
         */
        public OpenAiStreamingChatModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        /**
         * @param logger an alternate {@link Logger} to be used instead of the default one provided by Langchain4J for logging requests and responses.
         * @return {@code this}.
         */
        public OpenAiStreamingChatModelBuilder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        /**
         * Sets custom HTTP headers.
         */
        public OpenAiStreamingChatModelBuilder customHeaders(Map<String, String> customHeaders) {
            this.customHeadersSupplier = () -> customHeaders;
            return this;
        }

        /**
         * Sets a supplier for custom HTTP headers.
         * The supplier is called before each request, allowing dynamic header values.
         * For example, this is useful for OAuth2 tokens that expire and need refreshing.
         */
        public OpenAiStreamingChatModelBuilder customHeaders(Supplier<Map<String, String>> customHeadersSupplier) {
            this.customHeadersSupplier = customHeadersSupplier;
            return this;
        }

        /**
         * Sets custom URL query parameters
         */
        public OpenAiStreamingChatModelBuilder customQueryParams(Map<String, String> customQueryParams) {
            this.customQueryParams = customQueryParams;
            return this;
        }

        /**
         * Sets custom HTTP body parameters
         */
        public OpenAiStreamingChatModelBuilder customParameters(Map<String, Object> customParameters) {
            this.customParameters = customParameters;
            return this;
        }

        /**
         * Sets the list of {@link ChatModelListener}s to be notified on each request and response.
         * Useful for logging, metrics, and observability integrations.
         *
         * @param listeners the chat model listeners
         * @return {@code this}
         */
        public OpenAiStreamingChatModelBuilder listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        /**
         * Sets the {@link ChatModelListener}s to be notified on each request and response.
         *
         * @param listeners the chat model listeners
         * @return {@code this}
         */
        public OpenAiStreamingChatModelBuilder listeners(ChatModelListener... listeners) {
            return listeners(asList(listeners));
        }

        public OpenAiStreamingChatModel build() {
            return new OpenAiStreamingChatModel(this);
        }
    }
}
