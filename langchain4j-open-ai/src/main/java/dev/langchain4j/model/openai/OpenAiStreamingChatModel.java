package dev.langchain4j.model.openai;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.TokenCountEstimator;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.internal.OpenAiClient;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionChoice;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionRequest;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionResponse;
import dev.langchain4j.model.openai.internal.chat.Delta;
import dev.langchain4j.model.openai.internal.shared.StreamOptions;
import dev.langchain4j.model.openai.spi.OpenAiStreamingChatModelBuilderFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.langchain4j.internal.Utils.copyIfNotNull;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.ModelProvider.OPEN_AI;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.DEFAULT_OPENAI_URL;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.DEFAULT_USER_AGENT;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.fromOpenAiResponseFormat;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.toOpenAiChatRequest;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.time.Duration.ofSeconds;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;

/**
 * Represents an OpenAI language model with a chat completion interface, such as gpt-3.5-turbo and gpt-4.
 * The model's response is streamed token by token and should be handled with {@link StreamingResponseHandler}.
 * You can find description of parameters <a href="https://platform.openai.com/docs/api-reference/chat/create">here</a>.
 */
public class OpenAiStreamingChatModel implements StreamingChatLanguageModel, TokenCountEstimator {

    private final OpenAiClient client;

    private final OpenAiChatRequestParameters defaultRequestParameters;
    private final Set<Capability> supportedCapabilities;
    private final Boolean strictJsonSchema;
    private final Boolean strictTools;

    private final Tokenizer tokenizer;

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
                .userAgent(DEFAULT_USER_AGENT)
                .customHeaders(builder.customHeaders)
                .build();

        ChatRequestParameters commonParameters;
        if (builder.defaultRequestParameters != null) {
            commonParameters = builder.defaultRequestParameters;
        } else {
            commonParameters = DefaultChatRequestParameters.builder().build();
        }

        OpenAiChatRequestParameters openAiParameters;
        if (builder.defaultRequestParameters instanceof OpenAiChatRequestParameters openAiChatRequestParameters) {
            openAiParameters = openAiChatRequestParameters;
        } else {
            openAiParameters = OpenAiChatRequestParameters.builder().build();
        }

        this.defaultRequestParameters = OpenAiChatRequestParameters.builder()
                // common parameters
                .modelName(getOrDefault(builder.modelName, commonParameters.modelName()))
                .temperature(getOrDefault(builder.temperature, commonParameters.temperature()))
                .topP(getOrDefault(builder.topP, commonParameters.topP()))
                .frequencyPenalty(getOrDefault(builder.frequencyPenalty, commonParameters.frequencyPenalty()))
                .presencePenalty(getOrDefault(builder.presencePenalty, commonParameters.presencePenalty()))
                .maxOutputTokens(getOrDefault(builder.maxTokens, commonParameters.maxOutputTokens()))
                .stopSequences(getOrDefault(builder.stop, () -> copyIfNotNull(commonParameters.stopSequences())))
                .toolSpecifications(copyIfNotNull(commonParameters.toolSpecifications()))
                .toolChoice(commonParameters.toolChoice())
                .responseFormat(getOrDefault(
                        fromOpenAiResponseFormat(builder.responseFormat), commonParameters.responseFormat()))
                // OpenAI-specific parameters
                .maxCompletionTokens(getOrDefault(builder.maxCompletionTokens, openAiParameters.maxCompletionTokens()))
                .logitBias(getOrDefault(builder.logitBias, () -> copyIfNotNull(openAiParameters.logitBias())))
                .parallelToolCalls(getOrDefault(builder.parallelToolCalls, openAiParameters.parallelToolCalls()))
                .seed(getOrDefault(builder.seed, openAiParameters.seed()))
                .user(getOrDefault(builder.user, openAiParameters.user()))
                .store(getOrDefault(builder.store, openAiParameters.store()))
                .metadata(getOrDefault(builder.metadata, () -> copyIfNotNull(openAiParameters.metadata())))
                .serviceTier(getOrDefault(builder.serviceTier, openAiParameters.serviceTier()))
                .reasoningEffort(openAiParameters.reasoningEffort())
                .build();
        this.supportedCapabilities = new HashSet<>(getOrDefault(builder.supportedCapabilities, emptySet()));
        this.strictJsonSchema = getOrDefault(builder.strictJsonSchema, false); // TODO move into OpenAI-specific params?
        this.strictTools = getOrDefault(builder.strictTools, false); // TODO move into OpenAI-specific params?

        this.tokenizer = getOrDefault(builder.tokenizer, OpenAiTokenizer::new);

        this.listeners = builder.listeners == null ? emptyList() : new ArrayList<>(builder.listeners);
    }

    /**
     * @deprecated please use {@link #defaultRequestParameters()} and then {@link ChatRequestParameters#modelName()} instead
     */
    @Deprecated(forRemoval = true)
    public String modelName() {
        return defaultRequestParameters.modelName();
    }

    @Override
    public OpenAiChatRequestParameters defaultRequestParameters() {
        return defaultRequestParameters;
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return supportedCapabilities;
    }

    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {

        OpenAiChatRequestParameters parameters = (OpenAiChatRequestParameters) chatRequest.parameters();
        InternalOpenAiHelper.validate(parameters);

        ChatCompletionRequest openAiRequest =
                toOpenAiChatRequest(chatRequest, parameters, strictTools, strictJsonSchema).stream(true)
                        .streamOptions(
                                StreamOptions.builder().includeUsage(true).build())
                        .build();

        OpenAiStreamingResponseBuilder openAiResponseBuilder = new OpenAiStreamingResponseBuilder();

        client.chatCompletion(openAiRequest)
                .onPartialResponse(partialResponse -> {
                    openAiResponseBuilder.append(partialResponse);
                    handle(partialResponse, handler);
                })
                .onComplete(() -> {
                    ChatResponse chatResponse = openAiResponseBuilder.build();
                    handler.onCompleteResponse(chatResponse);
                })
                .onError(handler::onError)
                .execute();
    }

    private static void handle(ChatCompletionResponse partialResponse, StreamingChatResponseHandler handler) {
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
    public List<ChatModelListener> listeners() {
        return listeners;
    }

    @Override
    public ModelProvider provider() {
        return OPEN_AI;
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
        private Boolean logRequests;
        private Boolean logResponses;
        private Tokenizer tokenizer;
        private Map<String, String> customHeaders;
        private List<ChatModelListener> listeners;
        private Set<Capability> supportedCapabilities;

        public OpenAiStreamingChatModelBuilder() {
            // This is public so it can be extended
        }

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

        public OpenAiStreamingChatModelBuilder projectId(String projectId) {
            this.projectId = projectId;
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

        public OpenAiStreamingChatModelBuilder supportedCapabilities(Set<Capability> supportedCapabilities) {
            this.supportedCapabilities = supportedCapabilities;
            return this;
        }

        public OpenAiStreamingChatModelBuilder supportedCapabilities(Capability... supportedCapabilities) {
            return supportedCapabilities(new HashSet<>(asList(supportedCapabilities)));
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
            return new OpenAiStreamingChatModel(this);
        }
    }
}
