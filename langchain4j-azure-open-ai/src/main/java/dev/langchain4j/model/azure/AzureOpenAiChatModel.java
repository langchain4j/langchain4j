package dev.langchain4j.model.azure;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.ModelProvider.AZURE_OPEN_AI;
import static dev.langchain4j.model.azure.InternalAzureOpenAiHelper.aiMessageFrom;
import static dev.langchain4j.model.azure.InternalAzureOpenAiHelper.finishReasonFrom;
import static dev.langchain4j.model.azure.InternalAzureOpenAiHelper.setupSyncClient;
import static dev.langchain4j.model.azure.InternalAzureOpenAiHelper.toAzureOpenAiResponseFormat;
import static dev.langchain4j.model.azure.InternalAzureOpenAiHelper.toOpenAiMessages;
import static dev.langchain4j.model.azure.InternalAzureOpenAiHelper.toToolChoice;
import static dev.langchain4j.model.azure.InternalAzureOpenAiHelper.toToolDefinitions;
import static dev.langchain4j.model.azure.InternalAzureOpenAiHelper.tokenUsageFrom;
import static dev.langchain4j.model.azure.InternalAzureOpenAiHelper.validate;
import static dev.langchain4j.model.chat.request.ToolChoice.REQUIRED;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.util.Collections.singletonList;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.models.AzureChatEnhancementConfiguration;
import com.azure.ai.openai.models.AzureChatExtensionConfiguration;
import com.azure.ai.openai.models.ChatCompletions;
import com.azure.ai.openai.models.ChatCompletionsOptions;
import com.azure.ai.openai.models.ChatCompletionsResponseFormat;
import com.azure.core.credential.KeyCredential;
import com.azure.core.credential.TokenCredential;
import com.azure.core.http.HttpClientProvider;
import com.azure.core.http.ProxyOptions;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.azure.spi.AzureOpenAiChatModelBuilderFactory;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents an OpenAI language model, hosted on Azure, that has a chat completion interface, such as gpt-3.5-turbo.
 * <p>
 * Mandatory parameters for initialization are: endpoint and apikey (or an alternate authentication method, see below for more information).
 * Optionally you can set serviceVersion (if not, the latest version is used) and deploymentName (if not, a default name is used).
 * You can also provide your own OpenAIClient instance, if you need more flexibility.
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
 * - This requires to add the `com.azure:azure-identity` dependency to your project, which is an optional dependency to this library.
 * - You need to provide a TokenCredential instance, using the tokenCredential() method in the Builder, or the tokenCredential parameter in the constructor.
 * As an example, DefaultAzureCredential can be used to authenticate the client: Set the values of the client ID, tenant ID, and
 * client secret of the AAD application as environment variables: AZURE_CLIENT_ID, AZURE_TENANT_ID, AZURE_CLIENT_SECRET.
 * Then, provide the DefaultAzureCredential instance to the builder: `builder.tokenCredential(new DefaultAzureCredentialBuilder().build())`.
 */
public class AzureOpenAiChatModel implements ChatModel {

    private static final Logger logger = LoggerFactory.getLogger(AzureOpenAiChatModel.class);

    private OpenAIClient client;
    private final AzureOpenAiChatRequestParameters defaultRequestParameters;

    @Deprecated
    private final ChatCompletionsResponseFormat chatCompletionsResponseFormat;

    private final Boolean strictJsonSchema;
    private final List<ChatModelListener> listeners;
    private final Set<Capability> supportedCapabilities;

    public AzureOpenAiChatModel(AzureOpenAiChatModel.Builder builder) {
        if (builder.openAIClient == null) {
            if (builder.tokenCredential != null) {
                this.client = setupSyncClient(
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
                this.client = setupSyncClient(
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
                this.client = setupSyncClient(
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
            this.client = builder.openAIClient;
        }

        ChatRequestParameters commonParameters;
        if (builder.defaultRequestParameters != null) {
            validate(builder.defaultRequestParameters);
            commonParameters = builder.defaultRequestParameters;
        } else {
            commonParameters = DefaultChatRequestParameters.builder().build();
        }

        AzureOpenAiChatRequestParameters azureOpenAiParameters;
        if (builder.defaultRequestParameters instanceof AzureOpenAiChatRequestParameters openAiChatRequestParameters) {
            azureOpenAiParameters = openAiChatRequestParameters;
        } else {
            azureOpenAiParameters = AzureOpenAiChatRequestParameters.builder().build();
        }

        this.defaultRequestParameters = AzureOpenAiChatRequestParameters.builder()
                // common parameters
                .modelName(getOrDefault(builder.deploymentName, commonParameters.modelName()))
                .temperature(getOrDefault(builder.temperature, commonParameters.temperature()))
                .topP(getOrDefault(builder.topP, commonParameters.topP()))
                .frequencyPenalty(getOrDefault(builder.frequencyPenalty, commonParameters.frequencyPenalty()))
                .presencePenalty(getOrDefault(builder.presencePenalty, commonParameters.presencePenalty()))
                .maxOutputTokens(getOrDefault(builder.maxTokens, commonParameters.maxOutputTokens()))
                .stopSequences(getOrDefault(builder.stop, commonParameters.stopSequences()))
                .toolSpecifications(commonParameters.toolSpecifications())
                .toolChoice(commonParameters.toolChoice())
                .responseFormat(commonParameters.responseFormat())
                // OpenAI-specific parameters
                .maxCompletionTokens(azureOpenAiParameters.maxCompletionTokens())
                .logitBias(getOrDefault(builder.logitBias, azureOpenAiParameters.logitBias()))
                .parallelToolCalls(azureOpenAiParameters.parallelToolCalls())
                .seed(getOrDefault(builder.seed, azureOpenAiParameters.seed()))
                .user(getOrDefault(builder.user, azureOpenAiParameters.user()))
                .store(azureOpenAiParameters.store())
                .metadata(azureOpenAiParameters.metadata())
                .dataSources(getOrDefault(builder.dataSources, azureOpenAiParameters.dataSources()))
                .enhancements(getOrDefault(builder.enhancements, azureOpenAiParameters.enhancements()))
                .build();

        this.chatCompletionsResponseFormat = builder.chatCompletionsResponseFormat;
        if (this.chatCompletionsResponseFormat != null && builder.responseFormat != null) {
            throw new IllegalArgumentException("You can't set both chatCompletionsResponseFormat and responseFormat");
        }
        this.strictJsonSchema = getOrDefault(builder.strictJsonSchema, false);
        this.listeners = copy(builder.listeners);
        this.supportedCapabilities = copy(builder.capabilities);
    }

    @Override
    public AzureOpenAiChatRequestParameters defaultRequestParameters() {
        return defaultRequestParameters;
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return supportedCapabilities;
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        AzureOpenAiChatRequestParameters parameters = (AzureOpenAiChatRequestParameters) chatRequest.parameters();
        validate(parameters);

        ChatCompletionsResponseFormat chatCompletionsResponseFormat = null;
        ResponseFormat responseFormat = parameters.responseFormat();
        ToolSpecification toolThatMustBeExecuted = null;
        if (parameters.toolChoice() == REQUIRED) {
            toolThatMustBeExecuted = parameters.toolSpecifications().get(0);
        }
        if (responseFormat != null) {
            chatCompletionsResponseFormat = toAzureOpenAiResponseFormat(parameters.responseFormat(), strictJsonSchema);
        } else {
            chatCompletionsResponseFormat = this.chatCompletionsResponseFormat;
        }
        ChatCompletionsOptions options = new ChatCompletionsOptions(toOpenAiMessages(chatRequest.messages()))
                .setModel(parameters.modelName())
                .setMaxTokens(parameters.maxOutputTokens())
                .setTemperature(parameters.temperature())
                .setTopP(parameters.topP())
                .setStop(parameters.stopSequences())
                .setPresencePenalty(parameters.presencePenalty())
                .setFrequencyPenalty(parameters.frequencyPenalty())
                .setResponseFormat(chatCompletionsResponseFormat)
                .setMaxCompletionTokens(parameters.maxCompletionTokens())
                .setLogitBias(parameters.logitBias())
                .setParallelToolCalls(parameters.parallelToolCalls())
                .setSeed(parameters.seed())
                .setUser(parameters.user())
                .setStore(parameters.store())
                .setMetadata(parameters.metadata())
                .setDataSources(parameters.dataSources())
                .setEnhancements(parameters.enhancements());

        if (toolThatMustBeExecuted != null) {
            options.setTools(toToolDefinitions(singletonList(toolThatMustBeExecuted)));
            options.setToolChoice(toToolChoice(toolThatMustBeExecuted));
        }
        if (!isNullOrEmpty(parameters.toolSpecifications())) {
            options.setTools(toToolDefinitions(parameters.toolSpecifications()));
        }

        ChatCompletions chatCompletions = client.getChatCompletions(parameters.modelName(), options);

        ChatResponseMetadata chatResponseMetadata = ChatResponseMetadata.builder()
                .id(chatCompletions.getId())
                .modelName(chatCompletions.getModel())
                .tokenUsage(tokenUsageFrom(chatCompletions.getUsage()))
                .finishReason(
                        finishReasonFrom(chatCompletions.getChoices().get(0).getFinishReason()))
                .build();
        return ChatResponse.builder()
                .aiMessage(aiMessageFrom(chatCompletions.getChoices().get(0).getMessage()))
                .metadata(chatResponseMetadata)
                .build();
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
        for (AzureOpenAiChatModelBuilderFactory factory : loadFactories(AzureOpenAiChatModelBuilderFactory.class)) {
            return factory.get();
        }
        return new Builder();
    }

    public static class Builder {

        private String endpoint;
        private String serviceVersion;
        private String apiKey;
        private KeyCredential keyCredential;
        private TokenCredential tokenCredential;
        private HttpClientProvider httpClientProvider;
        private ChatRequestParameters defaultRequestParameters;
        private String deploymentName;
        private Integer maxTokens;
        private Double temperature;
        private Double topP;
        private Map<String, Integer> logitBias;
        private String user;
        private List<String> stop;
        private Double presencePenalty;
        private Double frequencyPenalty;
        private List<AzureChatExtensionConfiguration> dataSources;
        private AzureChatEnhancementConfiguration enhancements;
        private Long seed;
        private ChatCompletionsResponseFormat chatCompletionsResponseFormat;
        private ResponseFormat responseFormat;
        private Boolean strictJsonSchema;
        private Duration timeout;
        private Integer maxRetries;
        private ProxyOptions proxyOptions;
        private boolean logRequestsAndResponses;
        private OpenAIClient openAIClient;
        private String userAgentSuffix;
        private List<ChatModelListener> listeners;
        private Map<String, String> customHeaders;
        private Set<Capability> capabilities;

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
         * Sets default common {@link ChatRequestParameters} or OpenAI-specific {@link AzureOpenAiChatRequestParameters}.
         * <br>
         * When a parameter is set via an individual builder method (e.g., {@link #deploymentName(String)}),
         * its value takes precedence over the same parameter set via {@link ChatRequestParameters}.
         */
        public Builder defaultRequestParameters(ChatRequestParameters parameters) {
            this.defaultRequestParameters = parameters;
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

        /**
         * @deprecated For JSON output, you can replace `.responseFormat(new ChatCompletionsJsonResponseFormat())` with a `JsonSchema` in the `ResponseFormat`. You can then use `.strictJsonSchema(true)`to force JSON schema adherence.
         */
        @Deprecated(forRemoval = true)
        public Builder responseFormat(ChatCompletionsResponseFormat chatCompletionsResponseFormat) {
            this.chatCompletionsResponseFormat = chatCompletionsResponseFormat;
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

        public Builder logRequestsAndResponses(Boolean logRequestsAndResponses) {
            this.logRequestsAndResponses = logRequestsAndResponses;
            return this;
        }

        public Builder userAgentSuffix(String userAgentSuffix) {
            this.userAgentSuffix = userAgentSuffix;
            return this;
        }

        /**
         * Sets the Azure OpenAI client. This is an optional parameter, if you need more flexibility than using the endpoint, serviceVersion, apiKey, deploymentName parameters.
         *
         * @param openAIClient The Azure OpenAI client.
         * @return builder
         */
        public Builder openAIClient(OpenAIClient openAIClient) {
            this.openAIClient = openAIClient;
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

        public Builder supportedCapabilities(Set<Capability> capabilities) {
            this.capabilities = capabilities;
            return this;
        }

        public AzureOpenAiChatModel build() {
            return new AzureOpenAiChatModel(this);
        }
    }
}
