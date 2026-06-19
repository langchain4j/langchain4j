package dev.langchain4j.model.openaiofficial;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.model.openaiofficial.OpenAiOfficialResponsesStreamingChatModel.buildAiMessage;
import static dev.langchain4j.model.openaiofficial.OpenAiOfficialResponsesStreamingChatModel.buildRequestParams;
import static dev.langchain4j.model.openaiofficial.OpenAiOfficialResponsesStreamingChatModel.buildResponseMetadata;
import static dev.langchain4j.model.openaiofficial.OpenAiOfficialResponsesStreamingChatModel.extractEncryptedReasoning;
import static dev.langchain4j.model.openaiofficial.OpenAiOfficialResponsesStreamingChatModel.extractReasoningSummary;
import static dev.langchain4j.model.openaiofficial.OpenAiOfficialResponsesStreamingChatModel.extractText;
import static dev.langchain4j.model.openaiofficial.OpenAiOfficialResponsesStreamingChatModel.extractTokenUsage;
import static dev.langchain4j.model.openaiofficial.OpenAiOfficialResponsesStreamingChatModel.extractToolExecutionRequests;
import static dev.langchain4j.model.openaiofficial.OpenAiOfficialResponsesStreamingChatModel.mapStatusToFinishReason;
import static dev.langchain4j.model.openaiofficial.OpenAiOfficialResponsesStreamingChatModel.validate;
import static dev.langchain4j.model.openaiofficial.setup.OpenAiOfficialSetup.setupSyncClient;
import static java.util.Arrays.asList;

import com.openai.azure.AzureOpenAIServiceVersion;
import com.openai.client.OpenAIClient;
import com.openai.credential.Credential;
import com.openai.models.Reasoning;
import com.openai.models.ReasoningEffort;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.Tool;
import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.internal.ExceptionMapper;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.net.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ChatModel implementation using the official OpenAI Java client for the Responses API.
 */
@Experimental
public class OpenAiOfficialResponsesChatModel implements ChatModel {

    private final OpenAIClient client;
    private final OpenAiOfficialResponsesChatRequestParameters defaultRequestParameters;
    private final List<ChatModelListener> listeners;

    private OpenAiOfficialResponsesChatModel(Builder builder) {
        this.client = builder.client != null
                ? builder.client
                : setupSyncClient(
                        builder.baseUrl,
                        builder.apiKey,
                        builder.credential,
                        builder.microsoftFoundryDeploymentName,
                        builder.azureOpenAIServiceVersion,
                        builder.organizationId,
                        builder.isMicrosoftFoundry,
                        builder.isGitHubModels,
                        builder.modelName,
                        builder.timeout,
                        builder.maxRetries,
                        builder.proxy,
                        builder.customHeaders);

        ChatRequestParameters commonParameters;
        if (builder.defaultRequestParameters != null) {
            validate(builder.defaultRequestParameters);
            commonParameters = builder.defaultRequestParameters;
        } else {
            commonParameters = DefaultChatRequestParameters.EMPTY;
        }

        OpenAiOfficialResponsesChatRequestParameters responsesParameters =
                commonParameters instanceof OpenAiOfficialResponsesChatRequestParameters p
                        ? p
                        : OpenAiOfficialResponsesChatRequestParameters.EMPTY;

        this.defaultRequestParameters = OpenAiOfficialResponsesChatRequestParameters.builder()
                .modelName(ensureNotNull(getOrDefault(builder.modelName, commonParameters.modelName()), "modelName"))
                .temperature(getOrDefault(builder.temperature, commonParameters.temperature()))
                .topP(getOrDefault(builder.topP, commonParameters.topP()))
                .maxOutputTokens(getOrDefault(builder.maxOutputTokens, commonParameters.maxOutputTokens()))
                .toolSpecifications(getOrDefault(builder.toolSpecifications, commonParameters.toolSpecifications()))
                .toolChoice(getOrDefault(builder.toolChoice, commonParameters.toolChoice()))
                .responseFormat(getOrDefault(builder.responseFormat, commonParameters.responseFormat()))
                .previousResponseId(getOrDefault(builder.previousResponseId, responsesParameters.previousResponseId()))
                .maxToolCalls(getOrDefault(builder.maxToolCalls, responsesParameters.maxToolCalls()))
                .parallelToolCalls(getOrDefault(builder.parallelToolCalls, responsesParameters.parallelToolCalls()))
                .topLogprobs(getOrDefault(builder.topLogprobs, responsesParameters.topLogprobs()))
                .truncation(getOrDefault(builder.truncation, responsesParameters.truncation()))
                .include(getOrDefault(builder.include, responsesParameters.include()))
                .serviceTier(getOrDefault(builder.serviceTier, responsesParameters.serviceTier()))
                .safetyIdentifier(getOrDefault(builder.safetyIdentifier, responsesParameters.safetyIdentifier()))
                .promptCacheKey(getOrDefault(builder.promptCacheKey, responsesParameters.promptCacheKey()))
                .promptCacheRetention(
                        getOrDefault(builder.promptCacheRetention, responsesParameters.promptCacheRetention()))
                .reasoningEffort(getOrDefault(builder.reasoningEffort, responsesParameters.reasoningEffort()))
                .reasoningSummary(getOrDefault(builder.reasoningSummary, responsesParameters.reasoningSummary()))
                .textVerbosity(getOrDefault(builder.textVerbosity, responsesParameters.textVerbosity()))
                .store(getOrDefault(builder.store, getOrDefault(responsesParameters.store(), false)))
                .strictTools(getOrDefault(builder.strictTools, responsesParameters.strictTools()))
                .strictJsonSchema(getOrDefault(builder.strictJsonSchema, responsesParameters.strictJsonSchema()))
                .serverTools(getOrDefault(builder.serverTools, responsesParameters.serverTools()))
                .build();

        this.listeners = copy(builder.listeners);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        validate(chatRequest.parameters());
        OpenAiOfficialResponsesChatRequestParameters parameters =
                (OpenAiOfficialResponsesChatRequestParameters) chatRequest.parameters();

        try {
            ResponseCreateParams params = buildRequestParams(chatRequest, parameters);
            Response response = client.responses().create(params);

            String text = extractText(response);
            String thinking = extractReasoningSummary(response);
            String encryptedReasoning = extractEncryptedReasoning(response);
            List<ToolExecutionRequest> toolExecutionRequests = extractToolExecutionRequests(response);

            AiMessage aiMessage = buildAiMessage(text, thinking, toolExecutionRequests, encryptedReasoning);

            String finishReason = response.status()
                    .map(status -> mapStatusToFinishReason(status.asString(), !toolExecutionRequests.isEmpty()))
                    .orElse(null);

            OpenAiOfficialResponsesChatResponseMetadata metadata = buildResponseMetadata(
                    response.id(), parameters.modelName(), response, finishReason, extractTokenUsage(response));

            return ChatResponse.builder()
                    .aiMessage(aiMessage)
                    .metadata(metadata)
                    .build();
        } catch (Exception e) {
            throw ExceptionMapper.DEFAULT.mapException(e);
        }
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return defaultRequestParameters;
    }

    @Override
    public List<ChatModelListener> listeners() {
        return listeners;
    }

    @Override
    public ModelProvider provider() {
        return ModelProvider.OPEN_AI;
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return Set.of(Capability.RESPONSE_FORMAT_JSON_SCHEMA);
    }

    public static class Builder {

        private String baseUrl;
        private String apiKey;
        private Credential credential;
        private String microsoftFoundryDeploymentName;
        private AzureOpenAIServiceVersion azureOpenAIServiceVersion;
        private String organizationId;
        private boolean isMicrosoftFoundry;
        private boolean isGitHubModels;
        private Map<String, String> customHeaders;
        private Duration timeout;
        private Integer maxRetries;
        private Proxy proxy;

        private OpenAIClient client;
        private String modelName;
        private Double temperature;
        private Double topP;
        private Integer maxOutputTokens;
        private Integer maxToolCalls;
        private Boolean parallelToolCalls;
        private String previousResponseId;
        private Integer topLogprobs;
        private String truncation;
        private List<String> include;
        private String serviceTier;
        private String safetyIdentifier;
        private String promptCacheKey;
        private String promptCacheRetention;
        private ReasoningEffort reasoningEffort;
        private Reasoning.Summary reasoningSummary;
        private String textVerbosity;
        private Boolean store;
        private List<ChatModelListener> listeners;
        private Boolean strictTools;
        private Boolean strictJsonSchema;
        private List<ToolSpecification> toolSpecifications;
        private ToolChoice toolChoice;
        private ResponseFormat responseFormat;
        private ChatRequestParameters defaultRequestParameters;
        private List<Tool> serverTools;

        /**
         * Sets the base URL of the OpenAI-compatible API. Defaults to {@code https://api.openai.com/v1}.
         *
         * @param baseUrl the base URL
         * @return {@code this}
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * Sets the OpenAI API key used to authenticate requests.
         *
         * @param apiKey the API key
         * @return {@code this}
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * Sets the {@link Credential} used to authenticate requests (alternative to {@link #apiKey(String)}).
         *
         * @param credential the credential
         * @return {@code this}
         */
        public Builder credential(Credential credential) {
            this.credential = credential;
            return this;
        }

        /**
         * Sets the Microsoft Foundry deployment name used when connecting to Azure OpenAI or Microsoft Foundry.
         *
         * @param microsoftFoundryDeploymentName the deployment name
         * @return {@code this}
         */
        public Builder microsoftFoundryDeploymentName(String microsoftFoundryDeploymentName) {
            this.microsoftFoundryDeploymentName = microsoftFoundryDeploymentName;
            return this;
        }

        /**
         * Sets the Azure OpenAI service API version when connecting to Azure OpenAI.
         *
         * @param azureOpenAIServiceVersion the Azure OpenAI service version
         * @return {@code this}
         */
        public Builder azureOpenAIServiceVersion(AzureOpenAIServiceVersion azureOpenAIServiceVersion) {
            this.azureOpenAIServiceVersion = azureOpenAIServiceVersion;
            return this;
        }

        /**
         * Sets the OpenAI organization ID sent with each request.
         *
         * @param organizationId the organization ID
         * @return {@code this}
         */
        public Builder organizationId(String organizationId) {
            this.organizationId = organizationId;
            return this;
        }

        /**
         * Configures the client to use Microsoft Foundry as the API provider.
         *
         * @param isMicrosoftFoundry {@code true} to use Microsoft Foundry
         * @return {@code this}
         */
        public Builder isMicrosoftFoundry(boolean isMicrosoftFoundry) {
            this.isMicrosoftFoundry = isMicrosoftFoundry;
            return this;
        }

        /**
         * Configures the client to use GitHub Models as the API provider.
         *
         * @param isGitHubModels {@code true} to use GitHub Models
         * @return {@code this}
         */
        public Builder isGitHubModels(boolean isGitHubModels) {
            this.isGitHubModels = isGitHubModels;
            return this;
        }

        /**
         * Sets additional HTTP headers sent with every request.
         *
         * @param customHeaders the custom headers map
         * @return {@code this}
         */
        public Builder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        /**
         * Sets the HTTP request timeout.
         *
         * @param timeout the request timeout
         * @return {@code this}
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Sets the maximum number of retries on transient errors.
         *
         * @param maxRetries the maximum number of retries
         * @return {@code this}
         */
        public Builder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * Sets the network proxy used for HTTP connections.
         *
         * @param proxy the network proxy
         * @return {@code this}
         */
        public Builder proxy(Proxy proxy) {
            this.proxy = proxy;
            return this;
        }

        /**
         * Sets a pre-configured {@link OpenAIClient} to use directly, bypassing all other connection settings.
         *
         * @param client the pre-configured client
         * @return {@code this}
         */
        public Builder client(OpenAIClient client) {
            this.client = client;
            return this;
        }

        /**
         * Sets the model name, e.g. {@code "gpt-4o"} or {@code "o3"}.
         *
         * @param modelName the model name
         * @return {@code this}
         */
        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /**
         * Sets the sampling temperature (0.0–2.0). Higher values produce more random output.
         *
         * @param temperature the sampling temperature
         * @return {@code this}
         */
        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        /**
         * Sets the nucleus sampling probability (0.0–1.0). Alternative to {@link #temperature(Double)}.
         *
         * @param topP the top-P value
         * @return {@code this}
         */
        public Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        /**
         * Sets the maximum number of output tokens to generate.
         *
         * @param maxOutputTokens the maximum number of output tokens
         * @return {@code this}
         */
        public Builder maxOutputTokens(Integer maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
            return this;
        }

        /**
         * Sets the maximum number of tool call rounds the model may perform in a single turn.
         *
         * @param maxToolCalls the maximum number of tool calls per turn
         * @return {@code this}
         */
        public Builder maxToolCalls(Integer maxToolCalls) {
            this.maxToolCalls = maxToolCalls;
            return this;
        }

        /**
         * Controls whether the model may invoke multiple tools in a single turn.
         *
         * @param parallelToolCalls {@code true} to allow parallel tool calls
         * @return {@code this}
         */
        public Builder parallelToolCalls(Boolean parallelToolCalls) {
            this.parallelToolCalls = parallelToolCalls;
            return this;
        }

        /**
         * Sets the ID of a prior response to continue from, enabling multi-turn Responses API conversations.
         *
         * @param previousResponseId the ID of the previous response
         * @return {@code this}
         */
        public Builder previousResponseId(String previousResponseId) {
            this.previousResponseId = previousResponseId;
            return this;
        }

        /**
         * Sets the number of most likely tokens to return with their log probabilities at each position.
         *
         * @param topLogprobs the number of top log probabilities to return
         * @return {@code this}
         */
        public Builder topLogprobs(Integer topLogprobs) {
            this.topLogprobs = topLogprobs;
            return this;
        }

        /**
         * Sets the truncation strategy for the context window, e.g. {@code "auto"} or {@code "disabled"}.
         *
         * @param truncation the truncation strategy
         * @return {@code this}
         */
        public Builder truncation(String truncation) {
            this.truncation = truncation;
            return this;
        }

        /**
         * Sets the list of additional fields to include in the response, e.g. reasoning item references.
         *
         * @param include the list of fields to include
         * @return {@code this}
         */
        public Builder include(List<String> include) {
            this.include = include;
            return this;
        }

        /**
         * When Enterprise Open AI subscription is used, service tier = "priority" puts requests into a
         * faster pool.
         */
        public Builder serviceTier(String serviceTier) {
            this.serviceTier = serviceTier;
            return this;
        }

        /**
         * Sets a safety identifier attached to this request for content filtering and abuse monitoring.
         *
         * @param safetyIdentifier the safety identifier
         * @return {@code this}
         */
        public Builder safetyIdentifier(String safetyIdentifier) {
            this.safetyIdentifier = safetyIdentifier;
            return this;
        }

        /**
         * Sets the prompt cache key used to match and reuse cached prompt prefixes.
         *
         * @param promptCacheKey the prompt cache key
         * @return {@code this}
         */
        public Builder promptCacheKey(String promptCacheKey) {
            this.promptCacheKey = promptCacheKey;
            return this;
        }

        /**
         * Sets the retention duration for the cached prompt, e.g. {@code "1h"} or {@code "7d"}.
         *
         * @param promptCacheRetention the cache retention duration string
         * @return {@code this}
         */
        public Builder promptCacheRetention(String promptCacheRetention) {
            this.promptCacheRetention = promptCacheRetention;
            return this;
        }

        /**
         * Sets the reasoning effort level for reasoning models (e.g. o1, o3).
         *
         * @param reasoningEffort the reasoning effort
         * @return {@code this}
         */
        public Builder reasoningEffort(ReasoningEffort reasoningEffort) {
            this.reasoningEffort = reasoningEffort;
            return this;
        }

        /**
         * Sets the level of detail for the reasoning summary included in the response.
         *
         * @param reasoningSummary the reasoning summary level
         * @return {@code this}
         */
        public Builder reasoningSummary(Reasoning.Summary reasoningSummary) {
            this.reasoningSummary = reasoningSummary;
            return this;
        }

        /**
         * Sets the text verbosity level for the model's output, e.g. {@code "low"} or {@code "high"}.
         *
         * @param textVerbosity the text verbosity level
         * @return {@code this}
         */
        public Builder textVerbosity(String textVerbosity) {
            this.textVerbosity = textVerbosity;
            return this;
        }

        /**
         * Controls whether the completion is stored for model distillation or evaluation.
         *
         * @param store {@code true} to store the completion
         * @return {@code this}
         */
        public Builder store(Boolean store) {
            this.store = store;
            return this;
        }

        /**
         * Sets the list of {@link ChatModelListener} instances for observability hooks.
         *
         * @param listeners the chat model listeners
         * @return {@code this}
         */
        public Builder listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        /**
         * Sets the {@link ChatModelListener} instances for observability hooks (varargs overload).
         *
         * @param listeners the chat model listeners
         * @return {@code this}
         */
        public Builder listeners(ChatModelListener... listeners) {
            return this.listeners(asList(listeners));
        }

        /**
         * Enables strict JSON schema validation for tool inputs.
         *
         * @param strictTools {@code true} to enforce strict tool schema validation
         * @return {@code this}
         */
        public Builder strictTools(Boolean strictTools) {
            this.strictTools = strictTools;
            return this;
        }

        /**
         * Enables strict JSON schema validation for structured output.
         *
         * @param strictJsonSchema {@code true} to enforce strict JSON schema validation
         * @return {@code this}
         */
        public Builder strictJsonSchema(Boolean strictJsonSchema) {
            this.strictJsonSchema = strictJsonSchema;
            return this;
        }

        /**
         * Sets the default tool specifications available to the model.
         *
         * @param toolSpecifications the list of tool specifications
         * @return {@code this}
         */
        public Builder toolSpecifications(List<ToolSpecification> toolSpecifications) {
            this.toolSpecifications = toolSpecifications;
            return this;
        }

        /**
         * Sets the default tool specifications available to the model (varargs overload).
         *
         * @param toolSpecifications the tool specifications
         * @return {@code this}
         */
        public Builder toolSpecifications(ToolSpecification... toolSpecifications) {
            return toolSpecifications(asList(toolSpecifications));
        }

        /**
         * Sets the default tool choice strategy controlling whether and how the model uses tools.
         *
         * @param toolChoice the tool choice
         * @return {@code this}
         */
        public Builder toolChoice(ToolChoice toolChoice) {
            this.toolChoice = toolChoice;
            return this;
        }

        /**
         * Sets the default response format, e.g. JSON mode or structured output schema.
         *
         * @param responseFormat the response format
         * @return {@code this}
         */
        public Builder responseFormat(ResponseFormat responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        /**
         * Sets default request parameters that are merged into every chat request.
         *
         * @param defaultRequestParameters the default chat request parameters
         * @return {@code this}
         */
        public Builder defaultRequestParameters(ChatRequestParameters defaultRequestParameters) {
            this.defaultRequestParameters = defaultRequestParameters;
            return this;
        }

        /**
         * Sets server-side tools (e.g. web search, code interpreter) provided by the Responses API.
         *
         * @param serverTools the list of server tools
         * @return {@code this}
         */
        public Builder serverTools(List<Tool> serverTools) {
            this.serverTools = serverTools;
            return this;
        }

        /**
         * Sets server-side tools (e.g. web search, code interpreter) provided by the Responses API (varargs overload).
         *
         * @param serverTools the server tools
         * @return {@code this}
         */
        public Builder serverTools(Tool... serverTools) {
            return serverTools(asList(serverTools));
        }

        public OpenAiOfficialResponsesChatModel build() {
            return new OpenAiOfficialResponsesChatModel(this);
        }
    }
}
