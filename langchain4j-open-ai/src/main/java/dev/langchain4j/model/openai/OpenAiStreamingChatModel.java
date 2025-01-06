package dev.langchain4j.model.openai;

import dev.ai4j.openai4j.OpenAiClient;
import dev.ai4j.openai4j.chat.ChatCompletionChoice;
import dev.ai4j.openai4j.chat.ChatCompletionRequest;
import dev.ai4j.openai4j.chat.ChatCompletionResponse;
import dev.ai4j.openai4j.chat.Delta;
import dev.ai4j.openai4j.chat.ResponseFormat;
import dev.ai4j.openai4j.chat.ResponseFormatType;
import dev.ai4j.openai4j.shared.StreamOptions;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
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
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.spi.OpenAiStreamingChatModelBuilderFactory;
import org.slf4j.Logger;

import java.net.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;

import static dev.ai4j.openai4j.chat.ResponseFormatType.JSON_SCHEMA;
import static dev.langchain4j.internal.Utils.copyIfNotNull;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.chat.request.ToolChoice.REQUIRED;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.DEFAULT_USER_AGENT;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.OPENAI_URL;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.convertHandler;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.createModelListenerRequest;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.createModelListenerResponse;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.toOpenAiMessages;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.toOpenAiResponseFormat;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.toOpenAiToolChoice;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.toTools;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.validate;
import static dev.langchain4j.model.openai.OpenAiModelName.GPT_3_5_TURBO;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.time.Duration.ofSeconds;
import static java.util.Collections.emptyList;

/**
 * Represents an OpenAI language model with a chat completion interface, such as gpt-3.5-turbo and gpt-4.
 * The model's response is streamed token by token and should be handled with {@link StreamingResponseHandler}.
 * You can find description of parameters <a href="https://platform.openai.com/docs/api-reference/chat/create">here</a>.
 */
public class OpenAiStreamingChatModel implements StreamingChatLanguageModel, TokenCountEstimator {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(OpenAiStreamingChatModel.class);

    private final OpenAiClient client;

    private final OpenAiChatRequestParameters defaultRequestParameters;
    private final Integer maxCompletionTokens;
    private final ResponseFormat responseFormat;
    private final Boolean strictJsonSchema;
    private final Boolean strictTools;

    private final Tokenizer tokenizer;

    private final List<ChatModelListener> listeners;

    public OpenAiStreamingChatModel(String baseUrl,
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
                                    Proxy proxy,
                                    Boolean logRequests,
                                    Boolean logResponses,
                                    Tokenizer tokenizer,
                                    Map<String, String> customHeaders,
                                    List<ChatModelListener> listeners) {

        timeout = getOrDefault(timeout, ofSeconds(60));

        this.client = OpenAiClient.builder()
                .baseUrl(getOrDefault(baseUrl, OPENAI_URL))
                .openAiApiKey(apiKey)
                .organizationId(organizationId)
                .callTimeout(timeout)
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .proxy(proxy)
                .logRequests(logRequests)
                .logStreamingResponses(logResponses)
                .userAgent(DEFAULT_USER_AGENT)
                .customHeaders(customHeaders)
                .build();

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
        this.responseFormat = responseFormat == null ? null : ResponseFormat.builder() // TODO move into OpenAI-specific params?
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
    public void chat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        validate(chatRequest.parameters());
        ResponseFormat openAiResponseFormat = toOpenAiResponseFormat(chatRequest.parameters().responseFormat(), this.strictJsonSchema);
        doChat(chatRequest, getOrDefault(openAiResponseFormat, this.responseFormat), handler);
    }

    @Override
    public void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(messages)
                .build();
        doChat(chatRequest, this.responseFormat, convertHandler(handler));
    }

    @Override
    public void generate(List<ChatMessage> messages,
                         List<ToolSpecification> toolSpecifications,
                         StreamingResponseHandler<AiMessage> handler) {
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(messages)
                .parameters(ChatRequestParameters.builder()
                        .toolSpecifications(toolSpecifications)
                        .build())
                .build();
        doChat(chatRequest, this.responseFormat, convertHandler(handler));
    }

    @Override
    public void generate(List<ChatMessage> messages,
                         ToolSpecification toolSpecification,
                         StreamingResponseHandler<AiMessage> handler) {
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(messages)
                .parameters(ChatRequestParameters.builder()
                        .toolSpecifications(toolSpecification)
                        .toolChoice(REQUIRED)
                        .build())
                .build();
        doChat(chatRequest, this.responseFormat, convertHandler(handler));
    }

    private void doChat(ChatRequest chatRequest,
                        ResponseFormat responseFormat,
                        StreamingChatResponseHandler handler
    ) {

        // TODO reuse code from OpenAiChatModel

        OpenAiChatRequestParameters requestParameters;
        if (chatRequest.parameters() instanceof OpenAiChatRequestParameters openAiChatRequestParameters) {
            requestParameters = openAiChatRequestParameters;
        } else {
            requestParameters = new OpenAiChatRequestParameters(chatRequest.parameters());
        }

        if (responseFormat != null
                && responseFormat.type() == JSON_SCHEMA
                && responseFormat.jsonSchema() == null) {
            responseFormat = null;
        }

        ChatCompletionRequest openAiRequest = ChatCompletionRequest.builder()
                .messages(toOpenAiMessages(chatRequest.messages()))
                // streaming
                .stream(true)
                .streamOptions(StreamOptions.builder()
                        .includeUsage(true)
                        .build())
                // common parameters
                .model(getOrDefault(requestParameters.modelName(), defaultRequestParameters.modelName()))
                .temperature(getOrDefault(requestParameters.temperature(), defaultRequestParameters.temperature()))
                .topP(getOrDefault(requestParameters.topP(), defaultRequestParameters.topP()))
                .frequencyPenalty(getOrDefault(requestParameters.frequencyPenalty(), defaultRequestParameters.frequencyPenalty()))
                .presencePenalty(getOrDefault(requestParameters.presencePenalty(), defaultRequestParameters.presencePenalty()))
                .maxTokens(getOrDefault(requestParameters.maxOutputTokens(), defaultRequestParameters.maxOutputTokens())) // TODO maxCompletionTokens
                .maxCompletionTokens(this.maxCompletionTokens)
                .stop(getOrDefault(requestParameters.stopSequences(), defaultRequestParameters.stopSequences()))
                .tools(toTools(getOrDefault(requestParameters.toolSpecifications(), defaultRequestParameters.toolSpecifications()), strictTools))
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

        OpenAiStreamingResponseBuilder openAiResponseBuilder = new OpenAiStreamingResponseBuilder();

        client.chatCompletion(openAiRequest)
                .onPartialResponse(partialResponse -> {
                    openAiResponseBuilder.append(partialResponse);
                    handle(partialResponse, handler);
                })
                .onComplete(() -> {
                    ChatResponse chatResponse = openAiResponseBuilder.build();

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

                    handler.onCompleteResponse(chatResponse);
                })
                .onError(error -> {
                    ChatResponse chatResponse = openAiResponseBuilder.build();

                    ChatModelResponse modelListenerPartialResponse = createModelListenerResponse(chatResponse);
                    ChatModelErrorContext errorContext = new ChatModelErrorContext(
                            error,
                            modelListenerRequest,
                            modelListenerPartialResponse,
                            attributes
                    );

                    listeners.forEach(listener -> {
                        try {
                            listener.onError(errorContext);
                        } catch (Exception e) {
                            log.warn("Exception while calling model listener", e);
                        }
                    });

                    handler.onError(error);
                })
                .execute();
    }

    private static void handle(ChatCompletionResponse partialResponse,
                               StreamingChatResponseHandler handler) {
        if (partialResponse == null) {
            return;
        }

        List<ChatCompletionChoice> choices = partialResponse.choices();
        if (choices == null || choices.isEmpty()) {
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
            handler.onPartialResponse(content);
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
    public static OpenAiStreamingChatModel withApiKey(String apiKey) {
        return builder().apiKey(apiKey).build();
    }

    public static OpenAiStreamingChatModelBuilder builder() {
        for (OpenAiStreamingChatModelBuilderFactory factory : loadFactories(OpenAiStreamingChatModelBuilderFactory.class)) {
            return factory.get();
        }
        return new OpenAiStreamingChatModelBuilder();
    }

    public static class OpenAiStreamingChatModelBuilder {

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
        private Proxy proxy;
        private Boolean logRequests;
        private Boolean logResponses;
        private Tokenizer tokenizer;
        private Map<String, String> customHeaders;
        private List<ChatModelListener> listeners;

        public OpenAiStreamingChatModelBuilder() {
            // This is public so it can be extended
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

        public OpenAiStreamingChatModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public OpenAiStreamingChatModelBuilder modelName(OpenAiChatModelName modelName) {
            this.modelName = modelName.toString();
            return this;
        }

        public OpenAiStreamingChatModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public OpenAiStreamingChatModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public OpenAiStreamingChatModelBuilder organizationId(String organizationId) {
            this.organizationId = organizationId;
            return this;
        }

        public OpenAiStreamingChatModelBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public OpenAiStreamingChatModelBuilder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public OpenAiStreamingChatModelBuilder stop(List<String> stop) {
            this.stop = stop;
            return this;
        }

        public OpenAiStreamingChatModelBuilder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public OpenAiStreamingChatModelBuilder maxCompletionTokens(Integer maxCompletionTokens) {
            this.maxCompletionTokens = maxCompletionTokens;
            return this;
        }

        public OpenAiStreamingChatModelBuilder presencePenalty(Double presencePenalty) {
            this.presencePenalty = presencePenalty;
            return this;
        }

        public OpenAiStreamingChatModelBuilder frequencyPenalty(Double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
            return this;
        }

        public OpenAiStreamingChatModelBuilder logitBias(Map<String, Integer> logitBias) {
            this.logitBias = logitBias;
            return this;
        }

        public OpenAiStreamingChatModelBuilder responseFormat(String responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        public OpenAiStreamingChatModelBuilder strictJsonSchema(Boolean strictJsonSchema) {
            this.strictJsonSchema = strictJsonSchema;
            return this;
        }

        public OpenAiStreamingChatModelBuilder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        public OpenAiStreamingChatModelBuilder user(String user) {
            this.user = user;
            return this;
        }

        public OpenAiStreamingChatModelBuilder strictTools(Boolean strictTools) {
            this.strictTools = strictTools;
            return this;
        }

        public OpenAiStreamingChatModelBuilder parallelToolCalls(Boolean parallelToolCalls) {
            this.parallelToolCalls = parallelToolCalls;
            return this;
        }

        public OpenAiStreamingChatModelBuilder store(Boolean store) {
            this.store = store;
            return this;
        }

        public OpenAiStreamingChatModelBuilder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public OpenAiStreamingChatModelBuilder serviceTier(String serviceTier) {
            this.serviceTier = serviceTier;
            return this;
        }

        public OpenAiStreamingChatModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public OpenAiStreamingChatModelBuilder proxy(Proxy proxy) {
            this.proxy = proxy;
            return this;
        }

        public OpenAiStreamingChatModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public OpenAiStreamingChatModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public OpenAiStreamingChatModelBuilder tokenizer(Tokenizer tokenizer) {
            this.tokenizer = tokenizer;
            return this;
        }

        public OpenAiStreamingChatModelBuilder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        public OpenAiStreamingChatModelBuilder listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public OpenAiStreamingChatModel build() {
            return new OpenAiStreamingChatModel(
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
            return new StringJoiner(", ", OpenAiStreamingChatModelBuilder.class.getSimpleName() + "[", "]")
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
