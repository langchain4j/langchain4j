package dev.langchain4j.model.openai;

import dev.ai4j.openai4j.OpenAiClient;
import dev.ai4j.openai4j.OpenAiHttpException;
import dev.ai4j.openai4j.chat.ChatCompletionRequest;
import dev.ai4j.openai4j.chat.ChatCompletionResponse;
import dev.ai4j.openai4j.chat.ResponseFormat;
import dev.ai4j.openai4j.chat.ResponseFormatType;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.TokenCountEstimator;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequest;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponse;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.spi.OpenAiChatModelBuilderFactory;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;

import java.net.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;

import static dev.ai4j.openai4j.chat.ResponseFormatType.JSON_SCHEMA;
import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.copyIfNotNull;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;
import static dev.langchain4j.model.chat.request.ToolChoice.REQUIRED;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.DEFAULT_USER_AGENT;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.OPENAI_DEMO_API_KEY;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.OPENAI_DEMO_URL;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.OPENAI_URL;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.aiMessageFrom;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.convertResponse;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.createModelListenerRequest;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.createModelListenerResponse;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.finishReasonFrom;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.toOpenAiMessages;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.toOpenAiResponseFormat;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.toOpenAiToolChoice;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.toTools;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.tokenUsageFrom;
import static dev.langchain4j.model.openai.OpenAiModelName.GPT_3_5_TURBO;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.time.Duration.ofSeconds;
import static java.util.Collections.emptyList;

/**
 * Represents an OpenAI language model with a chat completion interface, such as gpt-3.5-turbo and gpt-4.
 * You can find description of parameters <a href="https://platform.openai.com/docs/api-reference/chat/create">here</a>.
 */
public class OpenAiChatModel implements ChatLanguageModel, TokenCountEstimator {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(OpenAiChatModel.class);

    private final OpenAiClient client;
    private final Integer maxRetries;

    private final OpenAiChatRequestParameters defaultRequestParameters;
    private final Integer maxCompletionTokens;
    private final ResponseFormat responseFormat;
    private final Boolean strictJsonSchema;
    private final Boolean strictTools;

    private final Tokenizer tokenizer;

    private final List<ChatModelListener> listeners;

    public OpenAiChatModel(String baseUrl,
                           String apiKey,
                           String organizationId,
                           ChatRequestParameters defaultRequestParameters,
                           String modelName,
                           Double temperature,
                           Double topP,
                           List<String> stop,
                           Integer maxTokens,
                           Integer maxCompletionTokens,
                           Double presencePenalty,
                           Double frequencyPenalty,
                           Map<String, Integer> logitBias,
                           String responseFormat,
                           Boolean strictJsonSchema,
                           Integer seed,
                           String user,
                           Boolean strictTools,
                           Boolean parallelToolCalls,
                           Boolean store,
                           Map<String, String> metadata,
                           String serviceTier,
                           Duration timeout,
                           Integer maxRetries,
                           Proxy proxy,
                           Boolean logRequests,
                           Boolean logResponses,
                           Tokenizer tokenizer,
                           Map<String, String> customHeaders,
                           List<ChatModelListener> listeners) {

        baseUrl = getOrDefault(baseUrl, OPENAI_URL);
        if (OPENAI_DEMO_API_KEY.equals(apiKey)) {
            baseUrl = OPENAI_DEMO_URL;
        }

        timeout = getOrDefault(timeout, ofSeconds(60));

        this.client = OpenAiClient.builder()
                .openAiApiKey(apiKey)
                .baseUrl(baseUrl)
                .organizationId(organizationId)
                .callTimeout(timeout)
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .proxy(proxy)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .userAgent(DEFAULT_USER_AGENT)
                .customHeaders(customHeaders)
                .build();
        this.maxRetries = getOrDefault(maxRetries, 3);

        ChatRequestParameters commonParameters;
        if (defaultRequestParameters != null) {
            commonParameters = defaultRequestParameters;
        } else {
            commonParameters = DefaultChatRequestParameters.builder().build();
        }

        OpenAiChatRequestParameters openAiParameters;
        if (defaultRequestParameters instanceof OpenAiChatRequestParameters openAiChatRequestParameters) {
            openAiParameters = openAiChatRequestParameters;
        } else {
            openAiParameters = OpenAiChatRequestParameters.builder().build();
        }

        this.defaultRequestParameters = OpenAiChatRequestParameters.builder()
                // common parameters
                .modelName(getOrDefault(getOrDefault(modelName, commonParameters.modelName()), GPT_3_5_TURBO))
                .temperature(getOrDefault(getOrDefault(temperature, commonParameters.temperature()), 0.7))
                .topP(getOrDefault(topP, commonParameters.topP()))
                .frequencyPenalty(getOrDefault(frequencyPenalty, commonParameters.frequencyPenalty()))
                .presencePenalty(getOrDefault(presencePenalty, commonParameters.presencePenalty()))
                .maxOutputTokens(getOrDefault(maxTokens, commonParameters.maxOutputTokens())) // TODO maxCompletionTokens
                .stopSequences(getOrDefault(stop, () -> copyIfNotNull(commonParameters.stopSequences())))
                .toolSpecifications(copyIfNotNull(commonParameters.toolSpecifications()))
                .toolChoice(commonParameters.toolChoice())
                .responseFormat(commonParameters.responseFormat()) // TODO take from responseFormat, fallback to commonParams
                // OpenAI-specific parameters
                .logitBias(getOrDefault(logitBias, () -> copyIfNotNull(openAiParameters.logitBias())))
                .parallelToolCalls(getOrDefault(parallelToolCalls, openAiParameters.parallelToolCalls()))
                .seed(getOrDefault(seed, openAiParameters.seed()))
                .user(getOrDefault(user, openAiParameters.user()))
                .store(getOrDefault(store, openAiParameters.store()))
                .metadata(getOrDefault(metadata, () -> copyIfNotNull(openAiParameters.metadata())))
                .serviceTier(getOrDefault(serviceTier, openAiParameters.serviceTier()))
                .build();
        this.maxCompletionTokens = maxCompletionTokens; // TODO move into OpenAI-specific params?
        this.responseFormat = responseFormat == null ? null : ResponseFormat.builder() // TODO
                .type(ResponseFormatType.valueOf(responseFormat.toUpperCase(Locale.ROOT)))
                .build();
        this.strictJsonSchema = getOrDefault(strictJsonSchema, false); // TODO move into OpenAI-specific params?
        this.strictTools = getOrDefault(strictTools, false); // TODO move into OpenAI-specific params?

        this.tokenizer = getOrDefault(tokenizer, OpenAiTokenizer::new);

        this.listeners = listeners == null ? emptyList() : new ArrayList<>(listeners);
    }

    // TODO deprecate
    public String modelName() {
        return defaultRequestParameters.modelName();
    }

    @Override
    public OpenAiChatRequestParameters defaultRequestParameters() {
        return defaultRequestParameters;
    }

    @Override
    public ChatResponse chat(ChatRequest chatRequest) {
        ChatRequestParameters parameters = chatRequest.parameters();
        InternalOpenAiHelper.validate(parameters);
        ResponseFormat openAiResponseFormat = toOpenAiResponseFormat(parameters.responseFormat(), this.strictJsonSchema);
        return doChat(chatRequest, getOrDefault(openAiResponseFormat, this.responseFormat));
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        Set<Capability> capabilities = new HashSet<>();
        if (responseFormat != null && responseFormat.type() == JSON_SCHEMA) {
            capabilities.add(RESPONSE_FORMAT_JSON_SCHEMA);
        }
        return capabilities;
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(messages)
                .build();
        ChatResponse chatResponse = doChat(chatRequest, this.responseFormat);
        return convertResponse(chatResponse);
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(messages)
                .toolSpecifications(toolSpecifications)
                .build();
        ChatResponse chatResponse = doChat(chatRequest, this.responseFormat);
        return convertResponse(chatResponse);
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, ToolSpecification toolSpecification) {
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(messages)
                .parameters(ChatRequestParameters.builder()
                        .toolSpecifications(toolSpecification)
                        .toolChoice(REQUIRED)
                        .build())
                .build();
        ChatResponse chatResponse = doChat(chatRequest, this.responseFormat);
        return convertResponse(chatResponse);
    }

    private ChatResponse doChat(ChatRequest chatRequest, ResponseFormat responseFormat) {

        OpenAiChatRequestParameters requestParameters = new OpenAiChatRequestParameters(chatRequest.parameters());

        if (responseFormat != null
                && responseFormat.type() == JSON_SCHEMA
                && responseFormat.jsonSchema() == null) {
            responseFormat = null;
        }

        ChatCompletionRequest openAiRequest = ChatCompletionRequest.builder()
                .messages(toOpenAiMessages(chatRequest.messages()))
                // common parameters
                .model(getOrDefault(requestParameters.modelName(), defaultRequestParameters.modelName()))
                .temperature(getOrDefault(requestParameters.temperature(), defaultRequestParameters.temperature()))
                .topP(getOrDefault(requestParameters.topP(), defaultRequestParameters.topP()))
                .frequencyPenalty(getOrDefault(requestParameters.frequencyPenalty(), defaultRequestParameters.frequencyPenalty()))
                .presencePenalty(getOrDefault(requestParameters.presencePenalty(), defaultRequestParameters.presencePenalty()))
                .maxTokens(getOrDefault(requestParameters.maxOutputTokens(), defaultRequestParameters.maxOutputTokens())) // TODO maxCompletionTokens
                .maxCompletionTokens(this.maxCompletionTokens)
                .stop(getOrDefault(requestParameters.stopSequences(), defaultRequestParameters.stopSequences()))
                .tools(toTools(getOrDefault(requestParameters.toolSpecifications(), defaultRequestParameters.toolSpecifications()), this.strictTools))
                .toolChoice(toOpenAiToolChoice(getOrDefault(requestParameters.toolChoice(), defaultRequestParameters.toolChoice())))
                .responseFormat(getOrDefault(responseFormat, () -> toOpenAiResponseFormat(defaultRequestParameters.responseFormat(), this.strictJsonSchema)))
                // OpenAI-specific parameters
                .logitBias(getOrDefault(requestParameters.logitBias(), defaultRequestParameters.logitBias()))
                .parallelToolCalls(getOrDefault(requestParameters.parallelToolCalls(), defaultRequestParameters.parallelToolCalls()))
                .seed(getOrDefault(requestParameters.seed(), defaultRequestParameters.seed()))
                .user(getOrDefault(requestParameters.user(), defaultRequestParameters.user()))
                .store(getOrDefault(requestParameters.store(), defaultRequestParameters.store()))
                .metadata(getOrDefault(requestParameters.metadata(), defaultRequestParameters.metadata()))
                .serviceTier(getOrDefault(requestParameters.serviceTier(), defaultRequestParameters.serviceTier()))
                .build();

        ChatModelRequest modelListenerRequest = createModelListenerRequest(
                openAiRequest,
                chatRequest.messages(),
                requestParameters.toolSpecifications()
        );
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        ChatModelRequestContext requestContext = new ChatModelRequestContext(modelListenerRequest, attributes);
        listeners.forEach(listener -> {
            try {
                listener.onRequest(requestContext);
            } catch (Exception e) {
                log.warn("Exception while calling model listener", e);
            }
        });

        try {
            ChatCompletionResponse openAiResponse = withRetry(() -> client.chatCompletion(openAiRequest).execute(), maxRetries);

            OpenAiChatResponseMetadata responseMetadata = OpenAiChatResponseMetadata.builder()
                    .id(openAiResponse.id())
                    .modelName(openAiResponse.model())
                    .tokenUsage(tokenUsageFrom(openAiResponse.usage()))
                    .finishReason(finishReasonFrom(openAiResponse.choices().get(0).finishReason()))
                    .created(openAiResponse.created().longValue())
                    .serviceTier(openAiResponse.serviceTier())
                    .systemFingerprint(openAiResponse.systemFingerprint())
                    .build();

            ChatResponse chatResponse = ChatResponse.builder()
                    .aiMessage(aiMessageFrom(openAiResponse))
                    .metadata(responseMetadata)
                    .build();

            ChatModelResponse modelListenerResponse = createModelListenerResponse(chatResponse);
            ChatModelResponseContext responseContext = new ChatModelResponseContext(
                    modelListenerResponse,
                    modelListenerRequest,
                    attributes
            );
            listeners.forEach(listener -> {
                try {
                    listener.onResponse(responseContext);
                } catch (Exception e) {
                    log.warn("Exception while calling model listener", e);
                }
            });

            return chatResponse;
        } catch (RuntimeException e) {

            Throwable error;
            if (e.getCause() instanceof OpenAiHttpException) {
                error = e.getCause();
            } else {
                error = e;
            }

            ChatModelErrorContext errorContext = new ChatModelErrorContext(
                    error,
                    modelListenerRequest,
                    null,
                    attributes
            );

            listeners.forEach(listener -> {
                try {
                    listener.onError(errorContext);
                } catch (Exception e2) {
                    log.warn("Exception while calling model listener", e2);
                }
            });

            throw e;
        }
    }

    @Override
    public int estimateTokenCount(List<ChatMessage> messages) {
        return tokenizer.estimateTokenCountInMessages(messages);
    }

    /**
     * @deprecated Please use {@code builder()} instead, and explicitly set the model name and,
     * if necessary, other parameters.
     * <b>The default values for the model name and temperature will be removed in future releases!</b>
     */
    @Deprecated(forRemoval = true)
    public static OpenAiChatModel withApiKey(String apiKey) {
        return builder().apiKey(apiKey).build();
    }

    public static OpenAiChatModelBuilder builder() {
        for (OpenAiChatModelBuilderFactory factory : loadFactories(OpenAiChatModelBuilderFactory.class)) {
            return factory.get();
        }
        return new OpenAiChatModelBuilder();
    }

    public static class OpenAiChatModelBuilder {

        private String baseUrl;
        private String apiKey;
        private String organizationId;

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
        private String responseFormat;
        private Boolean strictJsonSchema;
        private Integer seed;
        private String user;
        private Boolean strictTools;
        private Boolean parallelToolCalls;
        private Boolean store;
        private Map<String, String> metadata;
        private String serviceTier;

        private Duration timeout;
        private Integer maxRetries;
        private Proxy proxy;
        private Boolean logRequests;
        private Boolean logResponses;
        private Tokenizer tokenizer;
        private Map<String, String> customHeaders;
        private List<ChatModelListener> listeners;

        public OpenAiChatModelBuilder() {
            // This is public so it can be extended
        }

        /**
         * Sets default common {@link ChatRequestParameters} or OpenAI-specific {@link OpenAiChatRequestParameters}.
         * <br>
         * When a parameter is set via an individual builder method (e.g., {@link #modelName(String)}),
         * its value takes precedence over the same parameter set via {@link ChatRequestParameters}.
         */
        public OpenAiChatModelBuilder defaultRequestParameters(ChatRequestParameters parameters) {
            this.defaultRequestParameters = parameters;
            return this;
        }

        public OpenAiChatModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public OpenAiChatModelBuilder modelName(OpenAiChatModelName modelName) {
            this.modelName = modelName.toString();
            return this;
        }

        public OpenAiChatModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public OpenAiChatModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public OpenAiChatModelBuilder organizationId(String organizationId) {
            this.organizationId = organizationId;
            return this;
        }

        public OpenAiChatModelBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public OpenAiChatModelBuilder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public OpenAiChatModelBuilder stop(List<String> stop) {
            this.stop = stop;
            return this;
        }

        public OpenAiChatModelBuilder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public OpenAiChatModelBuilder maxCompletionTokens(Integer maxCompletionTokens) {
            this.maxCompletionTokens = maxCompletionTokens;
            return this;
        }

        public OpenAiChatModelBuilder presencePenalty(Double presencePenalty) {
            this.presencePenalty = presencePenalty;
            return this;
        }

        public OpenAiChatModelBuilder frequencyPenalty(Double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
            return this;
        }

        public OpenAiChatModelBuilder logitBias(Map<String, Integer> logitBias) {
            this.logitBias = logitBias;
            return this;
        }

        public OpenAiChatModelBuilder responseFormat(String responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        public OpenAiChatModelBuilder strictJsonSchema(Boolean strictJsonSchema) {
            this.strictJsonSchema = strictJsonSchema;
            return this;
        }

        public OpenAiChatModelBuilder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        public OpenAiChatModelBuilder user(String user) {
            this.user = user;
            return this;
        }

        public OpenAiChatModelBuilder strictTools(Boolean strictTools) {
            this.strictTools = strictTools;
            return this;
        }

        public OpenAiChatModelBuilder parallelToolCalls(Boolean parallelToolCalls) {
            this.parallelToolCalls = parallelToolCalls;
            return this;
        }

        public OpenAiChatModelBuilder store(Boolean store) {
            this.store = store;
            return this;
        }

        public OpenAiChatModelBuilder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public OpenAiChatModelBuilder serviceTier(String serviceTier) {
            this.serviceTier = serviceTier;
            return this;
        }

        public OpenAiChatModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public OpenAiChatModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public OpenAiChatModelBuilder proxy(Proxy proxy) {
            this.proxy = proxy;
            return this;
        }

        public OpenAiChatModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public OpenAiChatModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public OpenAiChatModelBuilder tokenizer(Tokenizer tokenizer) {
            this.tokenizer = tokenizer;
            return this;
        }

        public OpenAiChatModelBuilder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        public OpenAiChatModelBuilder listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public OpenAiChatModel build() {
            return new OpenAiChatModel(
                    this.baseUrl,
                    this.apiKey,
                    this.organizationId,
                    this.defaultRequestParameters,
                    this.modelName,
                    this.temperature,
                    this.topP,
                    this.stop,
                    this.maxTokens,
                    this.maxCompletionTokens,
                    this.presencePenalty,
                    this.frequencyPenalty,
                    this.logitBias,
                    this.responseFormat,
                    this.strictJsonSchema,
                    this.seed,
                    this.user,
                    this.strictTools,
                    this.parallelToolCalls,
                    this.store,
                    this.metadata,
                    this.serviceTier,
                    this.timeout,
                    this.maxRetries,
                    this.proxy,
                    this.logRequests,
                    this.logResponses,
                    this.tokenizer,
                    this.customHeaders,
                    this.listeners
            );
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", OpenAiChatModelBuilder.class.getSimpleName() + "[", "]")
                    .add("baseUrl='" + baseUrl + "'")
                    .add("organizationId='" + organizationId + "'")
                    .add("defaultRequestParameters='" + defaultRequestParameters + "'")
                    .add("modelName='" + modelName + "'")
                    .add("temperature=" + temperature)
                    .add("topP=" + topP)
                    .add("stop=" + stop)
                    .add("maxTokens=" + maxTokens)
                    .add("maxCompletionTokens=" + maxCompletionTokens)
                    .add("presencePenalty=" + presencePenalty)
                    .add("frequencyPenalty=" + frequencyPenalty)
                    .add("logitBias=" + logitBias)
                    .add("responseFormat='" + responseFormat + "'")
                    .add("strictJsonSchema=" + strictJsonSchema)
                    .add("seed=" + seed)
                    .add("user='" + user + "'")
                    .add("strictTools=" + strictTools)
                    .add("parallelToolCalls=" + parallelToolCalls)
                    .add("store=" + store)
                    .add("metadata=" + metadata)
                    .add("serviceTier=" + serviceTier)
                    .add("timeout=" + timeout)
                    .add("maxRetries=" + maxRetries)
                    .add("proxy=" + proxy)
                    .add("logRequests=" + logRequests)
                    .add("logResponses=" + logResponses)
                    .add("tokenizer=" + tokenizer)
                    .add("customHeaders=" + customHeaders)
                    .add("listeners=" + listeners)
                    .toString();
        }
    }
}
