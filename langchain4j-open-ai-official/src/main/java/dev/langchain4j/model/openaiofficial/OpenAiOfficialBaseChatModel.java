package dev.langchain4j.model.openaiofficial;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.openaiofficial.InternalOpenAiOfficialHelper.detectModelHost;
import static dev.langchain4j.model.openaiofficial.InternalOpenAiOfficialHelper.fromOpenAiResponseFormat;
import static dev.langchain4j.model.openaiofficial.InternalOpenAiOfficialHelper.setupASyncClient;
import static dev.langchain4j.model.openaiofficial.InternalOpenAiOfficialHelper.setupSyncClient;
import static dev.langchain4j.model.openaiofficial.InternalOpenAiOfficialHelper.validate;

import com.openai.azure.AzureOpenAIServiceVersion;
import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.credential.Credential;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import java.net.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

abstract class OpenAiOfficialBaseChatModel {

    protected OpenAIClient client;
    protected OpenAIClientAsync asyncClient;
    protected InternalOpenAiOfficialHelper.ModelHost modelHost;
    protected String modelName;
    protected OpenAiOfficialChatRequestParameters defaultRequestParameters;
    protected String responseFormat;
    protected Boolean strictJsonSchema;
    protected Boolean strictTools;
    protected TokenCountEstimator tokenCountEstimator;
    protected List<ChatModelListener> listeners;
    protected Set<Capability> supportedCapabilities;

    public void init(
            String baseUrl,
            String apiKey,
            Credential credential,
            String azureDeploymentName,
            AzureOpenAIServiceVersion azureOpenAIServiceVersion,
            String organizationId,
            boolean isAzure,
            boolean isGitHubModels,
            OpenAIClient openAIClient,
            OpenAIClientAsync openAIClientAsync,
            ChatRequestParameters defaultRequestParameters,
            String modelName,
            Double temperature,
            Double topP,
            List<String> stop,
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
            TokenCountEstimator tokenCountEstimator,
            Map<String, String> customHeaders,
            List<ChatModelListener> listeners,
            Set<Capability> capabilities,
            boolean isAsync) {

        this.modelHost =
                detectModelHost(isAzure, isGitHubModels, baseUrl, azureDeploymentName, azureOpenAIServiceVersion);
        this.modelName = modelName;

        if (isAsync) {
            this.asyncClient = setupASyncClient(
                    baseUrl,
                    apiKey,
                    credential,
                    azureDeploymentName,
                    azureOpenAIServiceVersion,
                    modelHost,
                    openAIClientAsync,
                    organizationId,
                    modelName,
                    timeout,
                    maxRetries,
                    proxy,
                    customHeaders);
        } else {
            this.client = setupSyncClient(
                    baseUrl,
                    apiKey,
                    credential,
                    azureDeploymentName,
                    azureOpenAIServiceVersion,
                    organizationId,
                    modelHost,
                    openAIClient,
                    modelName,
                    timeout,
                    maxRetries,
                    proxy,
                    customHeaders);
        }

        ChatRequestParameters commonParameters;
        if (defaultRequestParameters != null) {
            validate(defaultRequestParameters);
            commonParameters = defaultRequestParameters;
        } else {
            commonParameters = DefaultChatRequestParameters.EMPTY;
        }

        OpenAiOfficialChatRequestParameters openAiParameters;
        if (defaultRequestParameters instanceof OpenAiOfficialChatRequestParameters openAiChatRequestParameters) {
            openAiParameters = openAiChatRequestParameters;
        } else {
            openAiParameters = OpenAiOfficialChatRequestParameters.EMPTY;
        }

        this.defaultRequestParameters = OpenAiOfficialChatRequestParameters.builder()
                // common parameters
                .modelName(getOrDefault(modelName, commonParameters.modelName()))
                .temperature(getOrDefault(temperature, commonParameters.temperature()))
                .topP(getOrDefault(topP, commonParameters.topP()))
                .frequencyPenalty(getOrDefault(frequencyPenalty, commonParameters.frequencyPenalty()))
                .presencePenalty(getOrDefault(presencePenalty, commonParameters.presencePenalty()))
                .maxOutputTokens(getOrDefault(maxCompletionTokens, commonParameters.maxOutputTokens()))
                .stopSequences(getOrDefault(stop, commonParameters.stopSequences()))
                .toolSpecifications(commonParameters.toolSpecifications())
                .toolChoice(commonParameters.toolChoice())
                .responseFormat(getOrDefault(fromOpenAiResponseFormat(responseFormat), commonParameters.responseFormat()))
                // OpenAI-specific parameters
                .maxCompletionTokens(getOrDefault(maxCompletionTokens, openAiParameters.maxCompletionTokens()))
                .logitBias(getOrDefault(logitBias, openAiParameters.logitBias()))
                .parallelToolCalls(getOrDefault(parallelToolCalls, openAiParameters.parallelToolCalls()))
                .seed(getOrDefault(seed, openAiParameters.seed()))
                .user(getOrDefault(user, openAiParameters.user()))
                .store(getOrDefault(store, openAiParameters.store()))
                .metadata(getOrDefault(metadata, openAiParameters.metadata()))
                .serviceTier(getOrDefault(serviceTier, openAiParameters.serviceTier()))
                .reasoningEffort(openAiParameters.reasoningEffort())
                .build();

        if (modelHost.equals(InternalOpenAiOfficialHelper.ModelHost.AZURE_OPENAI)
                || modelHost.equals(InternalOpenAiOfficialHelper.ModelHost.GITHUB_MODELS)) {
            if (!this.defaultRequestParameters.modelName().equals(this.modelName)) {
                // The model name can't be changed in Azure OpenAI, where it's part of the URL.
                throw new UnsupportedFeatureException("Modifying the modelName is not supported");
            }
        }

        this.responseFormat = responseFormat;
        this.strictJsonSchema = getOrDefault(strictJsonSchema, false); // TODO move into OpenAI-specific params?
        this.strictTools = getOrDefault(strictTools, false); // TODO move into OpenAI-specific params?

        this.tokenCountEstimator = tokenCountEstimator;

        this.listeners = copy(listeners);
        this.supportedCapabilities = copy(capabilities);
    }

    public OpenAiOfficialChatRequestParameters defaultRequestParameters() {
        return defaultRequestParameters;
    }

    public Set<Capability> supportedCapabilities() {
        return this.supportedCapabilities;
    }

    public List<ChatModelListener> listeners() {
        return listeners;
    }

    public ModelProvider provider() {
        return switch (modelHost) {
            case OPENAI -> ModelProvider.OPEN_AI;
            case AZURE_OPENAI -> ModelProvider.AZURE_OPEN_AI;
            case GITHUB_MODELS -> ModelProvider.GITHUB_MODELS;
        };
    }
}
