package dev.langchain4j.model.azure;

import static dev.langchain4j.internal.Utils.copyIfNotNull;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.ModelProvider.AZURE_OPEN_AI;
import static dev.langchain4j.model.azure.InternalAzureOpenAiHelper.aiMessageFrom;
import static dev.langchain4j.model.azure.InternalAzureOpenAiHelper.createListenerRequest;
import static dev.langchain4j.model.azure.InternalAzureOpenAiHelper.createListenerResponse;
import static dev.langchain4j.model.azure.InternalAzureOpenAiHelper.finishReasonFrom;
import static dev.langchain4j.model.azure.InternalAzureOpenAiHelper.setupSyncClient;
import static dev.langchain4j.model.azure.InternalAzureOpenAiHelper.toAzureOpenAiResponseFormat;
import static dev.langchain4j.model.azure.InternalAzureOpenAiHelper.toOpenAiMessages;
import static dev.langchain4j.model.azure.InternalAzureOpenAiHelper.toToolChoice;
import static dev.langchain4j.model.azure.InternalAzureOpenAiHelper.toToolDefinitions;
import static dev.langchain4j.model.azure.InternalAzureOpenAiHelper.tokenUsageFrom;
import static dev.langchain4j.model.chat.request.ToolChoice.REQUIRED;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.models.AzureChatEnhancementConfiguration;
import com.azure.ai.openai.models.AzureChatExtensionConfiguration;
import com.azure.ai.openai.models.ChatCompletions;
import com.azure.ai.openai.models.ChatCompletionsOptions;
import com.azure.ai.openai.models.ChatCompletionsResponseFormat;
import com.azure.core.credential.KeyCredential;
import com.azure.core.credential.TokenCredential;
import com.azure.core.http.ProxyOptions;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.azure.spi.AzureOpenAiChatModelBuilderFactory;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ChatRequestValidator;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.Response;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
public class AzureOpenAiChatModel implements ChatLanguageModel {

    private static final Logger logger = LoggerFactory.getLogger(AzureOpenAiChatModel.class);

    private OpenAIClient client;
    private final String deploymentName;
    private final Integer maxTokens;
    private final Double temperature;
    private final Double topP;
    private final Map<String, Integer> logitBias;
    private final String user;
    private final List<String> stop;
    private final Double presencePenalty;
    private final Double frequencyPenalty;
    private final List<AzureChatExtensionConfiguration> dataSources;
    private final AzureChatEnhancementConfiguration enhancements;
    private final Long seed;

    @Deprecated
    private ChatCompletionsResponseFormat chatCompletionsResponseFormat;

    private final ResponseFormat responseFormat;
    private final Boolean strictJsonSchema;
    private final List<ChatModelListener> listeners;
    private Set<Capability> supportedCapabilities;

    public AzureOpenAiChatModel(
            OpenAIClient client,
            String deploymentName,
            Integer maxTokens,
            Double temperature,
            Double topP,
            Map<String, Integer> logitBias,
            String user,
            List<String> stop,
            Double presencePenalty,
            Double frequencyPenalty,
            List<AzureChatExtensionConfiguration> dataSources,
            AzureChatEnhancementConfiguration enhancements,
            Long seed,
            @Deprecated ChatCompletionsResponseFormat chatCompletionsResponseFormat,
            ResponseFormat responseFormat,
            Boolean strictJsonSchema,
            List<ChatModelListener> listeners,
            Set<Capability> capabilities) {

        this(
                deploymentName,
                maxTokens,
                temperature,
                topP,
                logitBias,
                user,
                stop,
                presencePenalty,
                frequencyPenalty,
                dataSources,
                enhancements,
                seed,
                chatCompletionsResponseFormat,
                responseFormat,
                strictJsonSchema,
                listeners,
                capabilities);
        this.client = client;
    }

    public AzureOpenAiChatModel(
            String endpoint,
            String serviceVersion,
            String apiKey,
            String deploymentName,
            Integer maxTokens,
            Double temperature,
            Double topP,
            Map<String, Integer> logitBias,
            String user,
            List<String> stop,
            Double presencePenalty,
            Double frequencyPenalty,
            List<AzureChatExtensionConfiguration> dataSources,
            AzureChatEnhancementConfiguration enhancements,
            Long seed,
            @Deprecated ChatCompletionsResponseFormat chatCompletionsResponseFormat,
            ResponseFormat responseFormat,
            Boolean strictJsonSchema,
            Duration timeout,
            Integer maxRetries,
            ProxyOptions proxyOptions,
            boolean logRequestsAndResponses,
            List<ChatModelListener> listeners,
            String userAgentSuffix,
            Map<String, String> customHeaders,
            Set<Capability> capabilities) {

        this(
                deploymentName,
                maxTokens,
                temperature,
                topP,
                logitBias,
                user,
                stop,
                presencePenalty,
                frequencyPenalty,
                dataSources,
                enhancements,
                seed,
                chatCompletionsResponseFormat,
                responseFormat,
                strictJsonSchema,
                listeners,
                capabilities);
        this.client = setupSyncClient(
                endpoint,
                serviceVersion,
                apiKey,
                timeout,
                maxRetries,
                proxyOptions,
                logRequestsAndResponses,
                userAgentSuffix,
                customHeaders);
    }

    public AzureOpenAiChatModel(
            String endpoint,
            String serviceVersion,
            KeyCredential keyCredential,
            String deploymentName,
            Integer maxTokens,
            Double temperature,
            Double topP,
            Map<String, Integer> logitBias,
            String user,
            List<String> stop,
            Double presencePenalty,
            Double frequencyPenalty,
            List<AzureChatExtensionConfiguration> dataSources,
            AzureChatEnhancementConfiguration enhancements,
            Long seed,
            @Deprecated ChatCompletionsResponseFormat chatCompletionsResponseFormat,
            ResponseFormat responseFormat,
            Boolean strictJsonSchema,
            Duration timeout,
            Integer maxRetries,
            ProxyOptions proxyOptions,
            boolean logRequestsAndResponses,
            List<ChatModelListener> listeners,
            String userAgentSuffix,
            Map<String, String> customHeaders,
            Set<Capability> capabilities) {

        this(
                deploymentName,
                maxTokens,
                temperature,
                topP,
                logitBias,
                user,
                stop,
                presencePenalty,
                frequencyPenalty,
                dataSources,
                enhancements,
                seed,
                chatCompletionsResponseFormat,
                responseFormat,
                strictJsonSchema,
                listeners,
                capabilities);
        this.client = setupSyncClient(
                endpoint,
                serviceVersion,
                keyCredential,
                timeout,
                maxRetries,
                proxyOptions,
                logRequestsAndResponses,
                userAgentSuffix,
                customHeaders);
    }

    public AzureOpenAiChatModel(
            String endpoint,
            String serviceVersion,
            TokenCredential tokenCredential,
            String deploymentName,
            Integer maxTokens,
            Double temperature,
            Double topP,
            Map<String, Integer> logitBias,
            String user,
            List<String> stop,
            Double presencePenalty,
            Double frequencyPenalty,
            List<AzureChatExtensionConfiguration> dataSources,
            AzureChatEnhancementConfiguration enhancements,
            Long seed,
            @Deprecated ChatCompletionsResponseFormat chatCompletionsResponseFormat,
            ResponseFormat responseFormat,
            Boolean strictJsonSchema,
            Duration timeout,
            Integer maxRetries,
            ProxyOptions proxyOptions,
            boolean logRequestsAndResponses,
            List<ChatModelListener> listeners,
            String userAgentSuffix,
            Map<String, String> customHeaders,
            Set<Capability> capabilities) {

        this(
                deploymentName,
                maxTokens,
                temperature,
                topP,
                logitBias,
                user,
                stop,
                presencePenalty,
                frequencyPenalty,
                dataSources,
                enhancements,
                seed,
                chatCompletionsResponseFormat,
                responseFormat,
                strictJsonSchema,
                listeners,
                capabilities);
        this.client = setupSyncClient(
                endpoint,
                serviceVersion,
                tokenCredential,
                timeout,
                maxRetries,
                proxyOptions,
                logRequestsAndResponses,
                userAgentSuffix,
                customHeaders);
    }

    private AzureOpenAiChatModel(
            String deploymentName,
            Integer maxTokens,
            Double temperature,
            Double topP,
            Map<String, Integer> logitBias,
            String user,
            List<String> stop,
            Double presencePenalty,
            Double frequencyPenalty,
            List<AzureChatExtensionConfiguration> dataSources,
            AzureChatEnhancementConfiguration enhancements,
            Long seed,
            @Deprecated ChatCompletionsResponseFormat chatCompletionsResponseFormat,
            ResponseFormat responseFormat,
            Boolean strictJsonSchema,
            List<ChatModelListener> listeners,
            Set<Capability> capabilities) {

        this.deploymentName = getOrDefault(deploymentName, "gpt-35-turbo");
        this.maxTokens = maxTokens;
        this.temperature = getOrDefault(temperature, 0.7);
        this.topP = topP;
        this.logitBias = logitBias;
        this.user = user;
        this.stop = stop;
        this.presencePenalty = presencePenalty;
        this.frequencyPenalty = frequencyPenalty;
        this.dataSources = dataSources;
        this.enhancements = enhancements;
        this.seed = seed;
        this.chatCompletionsResponseFormat = chatCompletionsResponseFormat;
        this.responseFormat = responseFormat;
        if (this.chatCompletionsResponseFormat != null && this.responseFormat != null) {
            throw new IllegalArgumentException("You can't set both chatCompletionsResponseFormat and responseFormat");
        }
        this.strictJsonSchema = getOrDefault(strictJsonSchema, false);
        this.listeners = listeners == null ? emptyList() : new ArrayList<>(listeners);
        this.supportedCapabilities = copyIfNotNull(capabilities);
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return supportedCapabilities;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {

        ChatRequestParameters parameters = request.parameters();
        ChatRequestValidator.validateParameters(parameters);
        if (parameters.toolChoice() == REQUIRED) {
            if (parameters.toolSpecifications().size() != 1) {
                throw new UnsupportedFeatureException(
                        String.format("%s.%s is currently supported only when there is a single tool",
                                ToolChoice.class.getSimpleName(), REQUIRED.name()));
            }
        }

        // If the response format is not specified in the request, use the one specified in the model
        ResponseFormat responseFormat = parameters.responseFormat();
        if (responseFormat == null) {
            responseFormat = this.responseFormat;
        }

        ToolSpecification toolThatMustBeExecuted = null;
        if (parameters.toolChoice() == REQUIRED) {
            toolThatMustBeExecuted = parameters.toolSpecifications().get(0);
        }

        Response<AiMessage> response = generate(
                request.messages(),
                parameters.toolSpecifications(),
                toolThatMustBeExecuted,
                responseFormat
        );

        return ChatResponse.builder()
                .aiMessage(response.content())
                .metadata(ChatResponseMetadata.builder()
                        .tokenUsage(response.tokenUsage())
                        .finishReason(response.finishReason())
                        .build())
                .build();
    }

    private Response<AiMessage> generate(
            List<ChatMessage> messages,
            List<ToolSpecification> toolSpecifications,
            ToolSpecification toolThatMustBeExecuted,
            ResponseFormat responseFormat) {
        ChatCompletionsResponseFormat chatCompletionsResponseFormat = null;
        if (responseFormat != null) {
            chatCompletionsResponseFormat = toAzureOpenAiResponseFormat(responseFormat, this.strictJsonSchema);
        } else {
            chatCompletionsResponseFormat = this.chatCompletionsResponseFormat;
        }
        ChatCompletionsOptions options = new ChatCompletionsOptions(toOpenAiMessages(messages))
                .setModel(deploymentName)
                .setMaxTokens(maxTokens)
                .setTemperature(temperature)
                .setTopP(topP)
                .setLogitBias(logitBias)
                .setUser(user)
                .setStop(stop)
                .setPresencePenalty(presencePenalty)
                .setFrequencyPenalty(frequencyPenalty)
                .setDataSources(dataSources)
                .setEnhancements(enhancements)
                .setSeed(seed)
                .setResponseFormat(chatCompletionsResponseFormat);

        if (toolThatMustBeExecuted != null) {
            options.setTools(toToolDefinitions(singletonList(toolThatMustBeExecuted)));
            options.setToolChoice(toToolChoice(toolThatMustBeExecuted));
        }
        if (!isNullOrEmpty(toolSpecifications)) {
            options.setTools(toToolDefinitions(toolSpecifications));
        }

        ChatRequest listenerRequest = createListenerRequest(options, messages, toolSpecifications);
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        ChatModelRequestContext requestContext =
                new ChatModelRequestContext(listenerRequest, provider(), attributes);
        listeners.forEach(listener -> {
            try {
                listener.onRequest(requestContext);
            } catch (Exception e) {
                logger.warn("Exception while calling model listener", e);
            }
        });

        try {
            ChatCompletions chatCompletions = client.getChatCompletions(deploymentName, options);
            Response<AiMessage> response = Response.from(
                    aiMessageFrom(chatCompletions.getChoices().get(0).getMessage()),
                    tokenUsageFrom(chatCompletions.getUsage()),
                    finishReasonFrom(chatCompletions.getChoices().get(0).getFinishReason()));

            ChatResponse listenerResponse =
                    createListenerResponse(chatCompletions.getId(), options.getModel(), response);
            ChatModelResponseContext responseContext =
                    new ChatModelResponseContext(listenerResponse, listenerRequest, provider(), attributes);
            listeners.forEach(listener -> {
                try {
                    listener.onResponse(responseContext);
                } catch (Exception e) {
                    logger.warn("Exception while calling model listener", e);
                }
            });

            return response;
        } catch (Exception exception) {

            ChatModelErrorContext errorContext =
                    new ChatModelErrorContext(exception, listenerRequest, provider(), attributes);

            listeners.forEach(listener -> {
                try {
                    listener.onError(errorContext);
                } catch (Exception e2) {
                    logger.warn("Exception while calling model listener", e2);
                }
            });

            throw exception;
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
            if (this.capabilities == null) {
                capabilities = new HashSet<>();
            }
            if (openAIClient == null) {
                if (tokenCredential != null) {
                    return new AzureOpenAiChatModel(
                            endpoint,
                            serviceVersion,
                            tokenCredential,
                            deploymentName,
                            maxTokens,
                            temperature,
                            topP,
                            logitBias,
                            user,
                            stop,
                            presencePenalty,
                            frequencyPenalty,
                            dataSources,
                            enhancements,
                            seed,
                            chatCompletionsResponseFormat,
                            responseFormat,
                            strictJsonSchema,
                            timeout,
                            maxRetries,
                            proxyOptions,
                            logRequestsAndResponses,
                            listeners,
                            userAgentSuffix,
                            customHeaders,
                            capabilities);
                } else if (keyCredential != null) {
                    return new AzureOpenAiChatModel(
                            endpoint,
                            serviceVersion,
                            keyCredential,
                            deploymentName,
                            maxTokens,
                            temperature,
                            topP,
                            logitBias,
                            user,
                            stop,
                            presencePenalty,
                            frequencyPenalty,
                            dataSources,
                            enhancements,
                            seed,
                            chatCompletionsResponseFormat,
                            responseFormat,
                            strictJsonSchema,
                            timeout,
                            maxRetries,
                            proxyOptions,
                            logRequestsAndResponses,
                            listeners,
                            userAgentSuffix,
                            customHeaders,
                            capabilities);
                }
                return new AzureOpenAiChatModel(
                        endpoint,
                        serviceVersion,
                        apiKey,
                        deploymentName,
                        maxTokens,
                        temperature,
                        topP,
                        logitBias,
                        user,
                        stop,
                        presencePenalty,
                        frequencyPenalty,
                        dataSources,
                        enhancements,
                        seed,
                        chatCompletionsResponseFormat,
                        responseFormat,
                        strictJsonSchema,
                        timeout,
                        maxRetries,
                        proxyOptions,
                        logRequestsAndResponses,
                        listeners,
                        userAgentSuffix,
                        customHeaders,
                        capabilities);
            } else {
                return new AzureOpenAiChatModel(
                        openAIClient,
                        deploymentName,
                        maxTokens,
                        temperature,
                        topP,
                        logitBias,
                        user,
                        stop,
                        presencePenalty,
                        frequencyPenalty,
                        dataSources,
                        enhancements,
                        seed,
                        chatCompletionsResponseFormat,
                        responseFormat,
                        strictJsonSchema,
                        listeners,
                        capabilities);
            }
        }
    }
}
