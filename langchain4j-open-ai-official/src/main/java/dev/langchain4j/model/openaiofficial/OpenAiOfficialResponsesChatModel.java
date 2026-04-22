package dev.langchain4j.model.openaiofficial;

import com.openai.azure.AzureOpenAIServiceVersion;
import com.openai.client.OpenAIClient;
import com.openai.credential.Credential;
import com.openai.models.Reasoning;
import com.openai.models.ReasoningEffort;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
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
import dev.langchain4j.model.output.FinishReason;

import java.net.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
                .promptCacheRetention(getOrDefault(builder.promptCacheRetention, responsesParameters.promptCacheRetention()))
                .reasoningEffort(getOrDefault(builder.reasoningEffort, responsesParameters.reasoningEffort()))
                .reasoningSummary(getOrDefault(builder.reasoningSummary, responsesParameters.reasoningSummary()))
                .textVerbosity(getOrDefault(builder.textVerbosity, responsesParameters.textVerbosity()))
                .store(getOrDefault(builder.store, getOrDefault(responsesParameters.store(), false)))
                .strictTools(getOrDefault(builder.strictTools, responsesParameters.strictTools()))
                .strictJsonSchema(getOrDefault(builder.strictJsonSchema, responsesParameters.strictJsonSchema()))
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

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder credential(Credential credential) {
            this.credential = credential;
            return this;
        }

        public Builder microsoftFoundryDeploymentName(String microsoftFoundryDeploymentName) {
            this.microsoftFoundryDeploymentName = microsoftFoundryDeploymentName;
            return this;
        }

        public Builder azureOpenAIServiceVersion(AzureOpenAIServiceVersion azureOpenAIServiceVersion) {
            this.azureOpenAIServiceVersion = azureOpenAIServiceVersion;
            return this;
        }

        public Builder organizationId(String organizationId) {
            this.organizationId = organizationId;
            return this;
        }

        public Builder isMicrosoftFoundry(boolean isMicrosoftFoundry) {
            this.isMicrosoftFoundry = isMicrosoftFoundry;
            return this;
        }

        public Builder isGitHubModels(boolean isGitHubModels) {
            this.isGitHubModels = isGitHubModels;
            return this;
        }

        public Builder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
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

        public Builder proxy(Proxy proxy) {
            this.proxy = proxy;
            return this;
        }

        public Builder client(OpenAIClient client) {
            this.client = client;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
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

        public Builder maxOutputTokens(Integer maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
            return this;
        }

        public Builder maxToolCalls(Integer maxToolCalls) {
            this.maxToolCalls = maxToolCalls;
            return this;
        }

        public Builder parallelToolCalls(Boolean parallelToolCalls) {
            this.parallelToolCalls = parallelToolCalls;
            return this;
        }

        public Builder previousResponseId(String previousResponseId) {
            this.previousResponseId = previousResponseId;
            return this;
        }

        public Builder topLogprobs(Integer topLogprobs) {
            this.topLogprobs = topLogprobs;
            return this;
        }

        public Builder truncation(String truncation) {
            this.truncation = truncation;
            return this;
        }

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

        public Builder safetyIdentifier(String safetyIdentifier) {
            this.safetyIdentifier = safetyIdentifier;
            return this;
        }

        public Builder promptCacheKey(String promptCacheKey) {
            this.promptCacheKey = promptCacheKey;
            return this;
        }

        public Builder promptCacheRetention(String promptCacheRetention) {
            this.promptCacheRetention = promptCacheRetention;
            return this;
        }

        public Builder reasoningEffort(ReasoningEffort reasoningEffort) {
            this.reasoningEffort = reasoningEffort;
            return this;
        }

        public Builder reasoningSummary(Reasoning.Summary reasoningSummary) {
            this.reasoningSummary = reasoningSummary;
            return this;
        }

        public Builder textVerbosity(String textVerbosity) {
            this.textVerbosity = textVerbosity;
            return this;
        }

        public Builder store(Boolean store) {
            this.store = store;
            return this;
        }

        public Builder listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public Builder listeners(ChatModelListener... listeners) {
            return this.listeners(asList(listeners));
        }

        public Builder strictTools(Boolean strictTools) {
            this.strictTools = strictTools;
            return this;
        }

        public Builder strictJsonSchema(Boolean strictJsonSchema) {
            this.strictJsonSchema = strictJsonSchema;
            return this;
        }

        public Builder toolSpecifications(List<ToolSpecification> toolSpecifications) {
            this.toolSpecifications = toolSpecifications;
            return this;
        }

        public Builder toolSpecifications(ToolSpecification... toolSpecifications) {
            return toolSpecifications(asList(toolSpecifications));
        }

        public Builder toolChoice(ToolChoice toolChoice) {
            this.toolChoice = toolChoice;
            return this;
        }

        public Builder responseFormat(ResponseFormat responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        public Builder defaultRequestParameters(ChatRequestParameters defaultRequestParameters) {
            this.defaultRequestParameters = defaultRequestParameters;
            return this;
        }

        public OpenAiOfficialResponsesChatModel build() {
            return new OpenAiOfficialResponsesChatModel(this);
        }
    }
}
