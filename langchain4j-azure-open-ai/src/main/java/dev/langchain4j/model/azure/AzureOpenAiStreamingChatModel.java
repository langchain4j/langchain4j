package dev.langchain4j.model.azure;

import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.withLoggingExceptions;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.copyIfNotNull;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNotNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.model.ModelProvider.AZURE_OPEN_AI;
import static dev.langchain4j.model.azure.InternalAzureOpenAiHelper.setupAsyncClient;
import static dev.langchain4j.model.azure.InternalAzureOpenAiHelper.toAzureOpenAiResponseFormat;
import static dev.langchain4j.model.azure.InternalAzureOpenAiHelper.toOpenAiMessages;
import static dev.langchain4j.model.azure.InternalAzureOpenAiHelper.toToolChoice;
import static dev.langchain4j.model.azure.InternalAzureOpenAiHelper.toToolDefinitions;
import static dev.langchain4j.model.azure.InternalAzureOpenAiHelper.validate;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.util.Arrays.asList;

import com.azure.ai.openai.OpenAIAsyncClient;
import com.azure.ai.openai.models.AzureChatEnhancementConfiguration;
import com.azure.ai.openai.models.AzureChatExtensionConfiguration;
import com.azure.ai.openai.models.ChatChoice;
import com.azure.ai.openai.models.ChatCompletions;
import com.azure.ai.openai.models.ChatCompletionsOptions;
import com.azure.ai.openai.models.ChatResponseMessage;
import com.azure.core.credential.KeyCredential;
import com.azure.core.credential.TokenCredential;
import com.azure.core.http.HttpClientProvider;
import com.azure.core.http.ProxyOptions;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.azure.spi.AzureOpenAiStreamingChatModelBuilderFactory;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.Response;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import reactor.core.publisher.Flux;

/**
 * Represents an OpenAI language model, hosted on Azure, that has a chat completion interface, such as gpt-3.5-turbo.
 * The model's response is streamed token by token and should be handled with {@link StreamingResponseHandler}.
 * <p>
 * Mandatory parameters for initialization are: endpoint and apikey (or an alternate authentication method, see below for more information).
 * Optionally you can set serviceVersion (if not, the latest version is used) and deploymentName (if not, a default name is used).
 * You can also provide your own {@link OpenAIAsyncClient} instance, if you need more flexibility.
 * <p>
 * There are 3 authentication methods:
 * <p>
 * 1. Azure OpenAI API Key Authentication: this is the most common method, using an Azure OpenAI API key.
 * You need to provide the OpenAI API Key as a parameter, using the apiKey() method in the Builder, or the apiKey parameter in the constructor:
 * For example, you would use `builder.apiKey("{key}")`.
 * <p>
 * 2. non-Azure OpenAI API Key Authentication: this method allows to use the OpenAI service, instead of Azure OpenAI.
 * You can use the nonAzureApiKey() method in the Builder, which will also automatically set the endpoint to "https://api.openai.com/v1".
 * For example, you would use `builder.nonAzureApiKey("{key}")`.
 * The constructor requires a KeyCredential instance, which can be created using `new AzureKeyCredential("{key}")`, and doesn't set up the endpoint.
 * <p>
 * 3. Azure OpenAI client with Microsoft Entra ID (formerly Azure Active Directory) credentials.
 * - This requires to add the `com.azure:azure-identity` dependency to your project.
 * - You need to provide a TokenCredential instance, using the tokenCredential() method in the Builder, or the tokenCredential parameter in the constructor.
 * As an example, DefaultAzureCredential can be used to authenticate the client: Set the values of the client ID, tenant ID, and
 * client secret of the AAD application as environment variables: AZURE_CLIENT_ID, AZURE_TENANT_ID, AZURE_CLIENT_SECRET.
 * Then, provide the DefaultAzureCredential instance to the builder: `builder.tokenCredential(new DefaultAzureCredentialBuilder().build())`.
 */
public class AzureOpenAiStreamingChatModel implements StreamingChatModel {

    private final OpenAIAsyncClient client;

    private final ChatRequestParameters defaultRequestParameters;

    private final TokenCountEstimator tokenCountEstimator;
    private final Map<String, Integer> logitBias;
    private final String user;
    private final List<AzureChatExtensionConfiguration> dataSources;
    private final AzureChatEnhancementConfiguration enhancements;
    private final Long seed;
    private final Boolean strictJsonSchema;

    private final List<ChatModelListener> listeners;
    private final Set<Capability> supportedCapabilities;

    public AzureOpenAiStreamingChatModel(Builder builder) {
        if (builder.openAIAsyncClient == null) {
            if (builder.tokenCredential != null) {
                this.client = setupAsyncClient(
                        builder.endpoint,
                        builder.serviceVersion,
                        builder.tokenCredential,
                        builder.timeout,
                        builder.maxRetries,
                        builder.httpClientProvider,
                        builder.proxyOptions,
                        builder.logRequestsAndResponses,
                        builder.userAgentSuffix,
                        builder.customHeaders);
            } else if (builder.keyCredential != null) {
                this.client = setupAsyncClient(
                        builder.endpoint,
                        builder.serviceVersion,
                        builder.keyCredential,
                        builder.timeout,
                        builder.maxRetries,
                        builder.httpClientProvider,
                        builder.proxyOptions,
                        builder.logRequestsAndResponses,
                        builder.userAgentSuffix,
                        builder.customHeaders);
            } else {
                this.client = setupAsyncClient(
                        builder.endpoint,
                        builder.serviceVersion,
                        builder.apiKey,
                        builder.timeout,
                        builder.maxRetries,
                        builder.httpClientProvider,
                        builder.proxyOptions,
                        builder.logRequestsAndResponses,
                        builder.userAgentSuffix,
                        builder.customHeaders);
            }
        } else {
            this.client = ensureNotNull(builder.openAIAsyncClient, "openAIAsyncClient");
        }

        ChatRequestParameters parameters;
        if (builder.defaultRequestParameters != null) {
            validate(builder.defaultRequestParameters);
            parameters = builder.defaultRequestParameters;
        } else {
            parameters = DefaultChatRequestParameters.EMPTY;
        }

        this.defaultRequestParameters = ChatRequestParameters.builder()
                .modelName(getOrDefault(builder.deploymentName, parameters.modelName()))
                .temperature(getOrDefault(builder.temperature, parameters.temperature()))
                .topP(getOrDefault(builder.topP, parameters.topP()))
                .frequencyPenalty(getOrDefault(builder.frequencyPenalty, parameters.frequencyPenalty()))
                .presencePenalty(getOrDefault(builder.presencePenalty, parameters.presencePenalty()))
                .maxOutputTokens(getOrDefault(builder.maxTokens, parameters.maxOutputTokens()))
                .stopSequences(getOrDefault(builder.stop, parameters.stopSequences()))
                .toolSpecifications(parameters.toolSpecifications())
                .toolChoice(parameters.toolChoice())
                .responseFormat(getOrDefault(builder.responseFormat, parameters.responseFormat()))
                .build();

        this.logitBias = copy(builder.logitBias);
        this.user = builder.user;
        this.dataSources = copyIfNotNull(builder.dataSources);
        this.enhancements = builder.enhancements;
        this.seed = builder.seed;
        this.strictJsonSchema = getOrDefault(builder.strictJsonSchema, false);

        this.tokenCountEstimator = builder.tokenCountEstimator;

        this.listeners = copy(builder.listeners);
        this.supportedCapabilities = copy(builder.supportedCapabilities);
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return defaultRequestParameters;
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return supportedCapabilities;
    }

    @Override
    public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
        ChatRequestParameters parameters = request.parameters();
        validate(parameters);

        ChatCompletionsOptions options = new ChatCompletionsOptions(toOpenAiMessages(request.messages()))
                .setModel(parameters.modelName())
                .setTemperature(parameters.temperature())
                .setTopP(parameters.topP())
                .setFrequencyPenalty(parameters.frequencyPenalty())
                .setPresencePenalty(parameters.presencePenalty())
                .setMaxTokens(parameters.maxOutputTokens())
                .setStop(parameters.stopSequences())
                .setResponseFormat(toAzureOpenAiResponseFormat(parameters.responseFormat(), this.strictJsonSchema))
                .setLogitBias(logitBias)
                .setUser(user)
                .setDataSources(dataSources)
                .setEnhancements(enhancements)
                .setSeed(seed);

        if (!parameters.toolSpecifications().isEmpty()) {
            options.setTools(toToolDefinitions(parameters.toolSpecifications()));
        }
        if (parameters.toolChoice() != null) {
            options.setToolChoice(toToolChoice(parameters.toolChoice()));
        }

        Integer inputTokenCount = null;
        if (tokenCountEstimator != null) {
            inputTokenCount = tokenCountEstimator.estimateTokenCountInMessages(request.messages());
        }

        InternalAzureOpenAiStreamingResponseBuilder responseBuilder = new InternalAzureOpenAiStreamingResponseBuilder(inputTokenCount);

        Flux<ChatCompletions> chatCompletionsStream = client.getChatCompletionsStream(parameters.modelName(), options);

        AtomicReference<String> responseId = new AtomicReference<>();
        AtomicReference<String> responseModelName = new AtomicReference<>();

        chatCompletionsStream.subscribe(
                chatCompletion -> {
                    responseBuilder.append(chatCompletion);
                    handle(chatCompletion, handler);

                    if (isNotNullOrBlank(chatCompletion.getId())) {
                        responseId.set(chatCompletion.getId());
                    }
                    if (isNotNullOrBlank(chatCompletion.getModel())) {
                        responseModelName.set(chatCompletion.getModel());
                    }
                },
                error -> {
                    RuntimeException mappedError = AzureOpenAiExceptionMapper.INSTANCE.mapException(error);
                    withLoggingExceptions(() -> handler.onError(mappedError));
                },
                () -> {
                    Response<AiMessage> response = responseBuilder.build(tokenCountEstimator);

                    ChatResponse chatResponse = ChatResponse.builder()
                            .aiMessage(response.content())
                            .metadata(ChatResponseMetadata.builder()
                                    .id(responseId.get())
                                    .modelName(responseModelName.get())
                                    .tokenUsage(response.tokenUsage())
                                    .finishReason(response.finishReason())
                                    .build())
                            .build();

                    try {
                        handler.onCompleteResponse(chatResponse);
                    } catch (Exception e) {
                        withLoggingExceptions(() -> handler.onError(e));
                    }
                });
    }

    private static void handle(ChatCompletions chatCompletions, StreamingChatResponseHandler handler) {
        List<ChatChoice> choices = chatCompletions.getChoices();
        if (choices == null || choices.isEmpty()) {
            return;
        }

        ChatResponseMessage delta = choices.get(0).getDelta();
        if (delta == null) {
            return;
        }

        String content = delta.getContent();
        if (!isNullOrEmpty(content)) {
            try {
                handler.onPartialResponse(content);
            } catch (Exception e) {
                withLoggingExceptions(() -> handler.onError(e));
            }
        }
    }

    @Override
    public List<ChatModelListener> listeners() {
        return listeners;
    }

    @Override
    public ModelProvider provider() {
        return AZURE_OPEN_AI;
    }

    public static Builder builder() {
        for (AzureOpenAiStreamingChatModelBuilderFactory factory :
                loadFactories(AzureOpenAiStreamingChatModelBuilderFactory.class)) {
            return factory.get();
        }
        return new Builder();
    }

    public static class Builder {

        private ChatRequestParameters defaultRequestParameters;

        private String endpoint;
        private String serviceVersion;
        private String apiKey;
        private KeyCredential keyCredential;
        private TokenCredential tokenCredential;
        private HttpClientProvider httpClientProvider;
        private String deploymentName;
        private TokenCountEstimator tokenCountEstimator;
        private Integer maxTokens;
        private Double temperature;
        private Double topP;
        private Map<String, Integer> logitBias;
        private String user;
        private List<String> stop;
        private Double presencePenalty;
        private Double frequencyPenalty;
        private Duration timeout;
        private List<AzureChatExtensionConfiguration> dataSources;
        private AzureChatEnhancementConfiguration enhancements;
        private Long seed;
        private ResponseFormat responseFormat;
        private Boolean strictJsonSchema;
        private Integer maxRetries;
        private ProxyOptions proxyOptions;
        private boolean logRequestsAndResponses;
        private OpenAIAsyncClient openAIAsyncClient;
        private String userAgentSuffix;
        private List<ChatModelListener> listeners;
        private Map<String, String> customHeaders;
        private Set<Capability> supportedCapabilities;

        public Builder defaultRequestParameters(ChatRequestParameters parameters) {
            this.defaultRequestParameters = parameters;
            return this;
        }

        /**
         * Sets the Azure OpenAI endpoint. This is a mandatory parameter.
         *
         * @param endpoint The Azure OpenAI endpoint in the format: https://{resource}.openai.azure.com/
         * @return builder
         */
        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        /**
         * Sets the Azure OpenAI API service version. This is a mandatory parameter.
         *
         * @param serviceVersion The Azure OpenAI API service version in the format: 2023-05-15
         * @return builder
         */
        public Builder serviceVersion(String serviceVersion) {
            this.serviceVersion = serviceVersion;
            return this;
        }

        /**
         * Sets the Azure OpenAI API key.
         *
         * @param apiKey The Azure OpenAI API key.
         * @return builder
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * Used to authenticate with the OpenAI service, instead of Azure OpenAI.
         * This automatically sets the endpoint to https://api.openai.com/v1.
         *
         * @param nonAzureApiKey The non-Azure OpenAI API key
         * @return builder
         */
        public Builder nonAzureApiKey(String nonAzureApiKey) {
            this.keyCredential = new KeyCredential(nonAzureApiKey);
            this.endpoint = "https://api.openai.com/v1";
            return this;
        }

        /**
         * Used to authenticate to Azure OpenAI with Azure Active Directory credentials.
         *
         * @param tokenCredential the credentials to authenticate with Azure Active Directory
         * @return builder
         */
        public Builder tokenCredential(TokenCredential tokenCredential) {
            this.tokenCredential = tokenCredential;
            return this;
        }

        /**
         * Sets the {@code HttpClientProvider} to use for creating the HTTP client to communicate with the OpenAI api.
         *
         * @param httpClientProvider The {@code HttpClientProvider} to use
         * @return builder
         */
        public Builder httpClientProvider(HttpClientProvider httpClientProvider) {
            this.httpClientProvider = httpClientProvider;
            return this;
        }

        /**
         * Sets the deployment name in Azure OpenAI. This is a mandatory parameter.
         *
         * @param deploymentName The Deployment name.
         * @return builder
         */
        public Builder deploymentName(String deploymentName) {
            this.deploymentName = deploymentName;
            return this;
        }

        public Builder tokenCountEstimator(TokenCountEstimator tokenCountEstimator) {
            this.tokenCountEstimator = tokenCountEstimator;
            return this;
        }

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
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

        public Builder logitBias(Map<String, Integer> logitBias) {
            this.logitBias = logitBias;
            return this;
        }

        public Builder user(String user) {
            this.user = user;
            return this;
        }

        public Builder stop(List<String> stop) {
            this.stop = stop;
            return this;
        }

        public Builder presencePenalty(Double presencePenalty) {
            this.presencePenalty = presencePenalty;
            return this;
        }

        public Builder frequencyPenalty(Double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
            return this;
        }

        public Builder dataSources(List<AzureChatExtensionConfiguration> dataSources) {
            this.dataSources = dataSources;
            return this;
        }

        public Builder enhancements(AzureChatEnhancementConfiguration enhancements) {
            this.enhancements = enhancements;
            return this;
        }

        public Builder seed(Long seed) {
            this.seed = seed;
            return this;
        }

        public Builder responseFormat(ResponseFormat responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        public Builder strictJsonSchema(Boolean strictJsonSchema) {
            this.strictJsonSchema = strictJsonSchema;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder proxyOptions(ProxyOptions proxyOptions) {
            this.proxyOptions = proxyOptions;
            return this;
        }

        public Builder logRequestsAndResponses(boolean logRequestsAndResponses) {
            this.logRequestsAndResponses = logRequestsAndResponses;
            return this;
        }

        /**
         * Sets the Azure OpenAI client. This is an optional parameter, if you need more flexibility than using the endpoint, serviceVersion, apiKey, deploymentName parameters.
         *
         * @param openAIAsyncClient The Azure OpenAI client.
         * @return builder
         */
        public Builder openAIAsyncClient(OpenAIAsyncClient openAIAsyncClient) {
            this.openAIAsyncClient = openAIAsyncClient;
            return this;
        }

        public Builder userAgentSuffix(String userAgentSuffix) {
            this.userAgentSuffix = userAgentSuffix;
            return this;
        }

        public Builder listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public Builder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        public Builder supportedCapabilities(Set<Capability> supportedCapabilities) {
            this.supportedCapabilities = supportedCapabilities;
            return this;
        }

        public Builder supportedCapabilities(Capability... supportedCapabilities) {
            return supportedCapabilities(new HashSet<>(asList(supportedCapabilities)));
        }

        public AzureOpenAiStreamingChatModel build() {
            return new AzureOpenAiStreamingChatModel(this);
        }
    }
}
