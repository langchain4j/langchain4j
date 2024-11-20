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
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.DEFAULT_USER_AGENT;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.OPENAI_DEMO_API_KEY;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.OPENAI_DEMO_URL;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.OPENAI_URL;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.aiMessageFrom;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.createModelListenerRequest;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.createModelListenerResponse;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.finishReasonFrom;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.toOpenAiMessages;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.toOpenAiResponseFormat;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.toTools;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.tokenUsageFrom;
import static dev.langchain4j.model.openai.OpenAiModelName.GPT_3_5_TURBO;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.time.Duration.ofSeconds;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

/**
 * Represents an OpenAI language model with a chat completion interface, such as gpt-3.5-turbo and gpt-4.
 * You can find description of parameters <a href="https://platform.openai.com/docs/api-reference/chat/create">here</a>.
 */
public class OpenAiChatModel implements ChatLanguageModel, TokenCountEstimator {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(OpenAiChatModel.class);
    private final OpenAiClient client;
    private final String modelName;
    private final Double temperature;
    private final Double topP;
    private final List<String> stop;
    private final Integer maxTokens;
    private final Integer maxCompletionTokens;
    private final Double presencePenalty;
    private final Double frequencyPenalty;
    private final Map<String, Integer> logitBias;
    private final ResponseFormat responseFormat;
    private final Boolean strictJsonSchema;
    private final Integer seed;
    private final String user;
    private final Boolean strictTools;
    private final Boolean parallelToolCalls;
    private final Integer maxRetries;
    private final Tokenizer tokenizer;
    private final List<ChatModelListener> listeners;

    public OpenAiChatModel(String baseUrl,
                           String apiKey,
                           String organizationId,
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
        this.modelName = getOrDefault(modelName, GPT_3_5_TURBO);
        this.temperature = getOrDefault(temperature, 0.7);
        this.topP = topP;
        this.stop = stop;
        this.maxTokens = maxTokens;
        this.maxCompletionTokens = maxCompletionTokens;
        this.presencePenalty = presencePenalty;
        this.frequencyPenalty = frequencyPenalty;
        this.logitBias = logitBias;
        this.responseFormat = responseFormat == null ? null : ResponseFormat.builder()
                .type(ResponseFormatType.valueOf(responseFormat.toUpperCase(Locale.ROOT)))
                .build();
        this.strictJsonSchema = getOrDefault(strictJsonSchema, false);
        this.seed = seed;
        this.user = user;
        this.strictTools = getOrDefault(strictTools, false);
        this.parallelToolCalls = parallelToolCalls;
        this.maxRetries = getOrDefault(maxRetries, 3);
        this.tokenizer = getOrDefault(tokenizer, OpenAiTokenizer::new);
        this.listeners = listeners == null ? emptyList() : new ArrayList<>(listeners);
    }

    public String modelName() {
        return modelName;
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
        return generate(messages, null, null, this.responseFormat);
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        return generate(messages, toolSpecifications, null, this.responseFormat);
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, ToolSpecification toolSpecification) {
        return generate(messages, singletonList(toolSpecification), toolSpecification, this.responseFormat);
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        Response<AiMessage> response = generate(
                request.messages(),
                request.toolSpecifications(),
                null,
                getOrDefault(toOpenAiResponseFormat(request.responseFormat(), strictJsonSchema), this.responseFormat)
        );
        return ChatResponse.builder()
                .aiMessage(response.content())
                .tokenUsage(response.tokenUsage())
                .finishReason(response.finishReason())
                .build();
    }

    private Response<AiMessage> generate(List<ChatMessage> messages,
                                         List<ToolSpecification> toolSpecifications,
                                         ToolSpecification toolThatMustBeExecuted,
                                         ResponseFormat responseFormat) {

        if (responseFormat != null
                && responseFormat.type() == JSON_SCHEMA
                && responseFormat.jsonSchema() == null) {
            responseFormat = null;
        }

        ChatCompletionRequest.Builder requestBuilder = ChatCompletionRequest.builder()
                .model(modelName)
                .messages(toOpenAiMessages(messages))
                .temperature(temperature)
                .topP(topP)
                .stop(stop)
                .maxTokens(maxTokens)
                .maxCompletionTokens(maxCompletionTokens)
                .presencePenalty(presencePenalty)
                .frequencyPenalty(frequencyPenalty)
                .logitBias(logitBias)
                .responseFormat(responseFormat)
                .seed(seed)
                .user(user)
                .parallelToolCalls(parallelToolCalls);

        if (toolSpecifications != null && !toolSpecifications.isEmpty()) {
            requestBuilder.tools(toTools(toolSpecifications, strictTools));
        }
        if (toolThatMustBeExecuted != null) {
            requestBuilder.toolChoice(toolThatMustBeExecuted.name());
        }

        ChatCompletionRequest request = requestBuilder.build();

        ChatModelRequest modelListenerRequest = createModelListenerRequest(request, messages, toolSpecifications);
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
            ChatCompletionResponse chatCompletionResponse = withRetry(() -> client.chatCompletion(request).execute(), maxRetries);

            Response<AiMessage> response = Response.from(
                    aiMessageFrom(chatCompletionResponse),
                    tokenUsageFrom(chatCompletionResponse.usage()),
                    finishReasonFrom(chatCompletionResponse.choices().get(0).finishReason())
            );

            ChatModelResponse modelListenerResponse = createModelListenerResponse(
                    chatCompletionResponse.id(),
                    chatCompletionResponse.model(),
                    response
            );
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

            return response;
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
