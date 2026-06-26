package dev.langchain4j.model.openai;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.Arrays.asList;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.http.client.HttpClientBuilder;
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
import java.util.List;
import java.util.Map;
import java.util.Set;

@Experimental
public class OpenAiResponsesChatModel implements ChatModel {

    private final OpenAiResponsesClient client;
    private final OpenAiResponsesChatRequestParameters defaultRequestParameters;
    private final List<ChatModelListener> listeners;

    private OpenAiResponsesChatModel(Builder builder) {
        this.client = OpenAiResponsesClient.builder()
                .httpClientBuilder(builder.httpClientBuilder)
                .baseUrl(builder.baseUrl)
                .apiKey(builder.apiKey)
                .organizationId(builder.organizationId)
                .logRequests(builder.logRequests)
                .logResponses(builder.logResponses)
                .build();

        ChatRequestParameters commonParameters;
        if (builder.defaultRequestParameters != null) {
            validate(builder.defaultRequestParameters);
            commonParameters = builder.defaultRequestParameters;
        } else {
            commonParameters = DefaultChatRequestParameters.EMPTY;
        }

        OpenAiResponsesChatRequestParameters responsesParameters =
                commonParameters instanceof OpenAiResponsesChatRequestParameters openAiResponsesParameters
                        ? openAiResponsesParameters
                        : OpenAiResponsesChatRequestParameters.EMPTY;

        this.defaultRequestParameters = OpenAiResponsesChatRequestParameters.builder()
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
        OpenAiResponsesChatRequestParameters parameters =
                (OpenAiResponsesChatRequestParameters) chatRequest.parameters();
        return client.chat(chatRequest, parameters);
    }

    private static void validate(final ChatRequestParameters parameters) {
        if (parameters.topK() != null) {
            throw new UnsupportedFeatureException("'topK' parameter is not supported by OpenAI Responses API");
        }
        if (parameters.frequencyPenalty() != null) {
            throw new UnsupportedFeatureException(
                    "'frequencyPenalty' parameter is not supported by OpenAI Responses API");
        }
        if (parameters.presencePenalty() != null) {
            throw new UnsupportedFeatureException(
                    "'presencePenalty' parameter is not supported by OpenAI Responses API");
        }
        if (parameters.stopSequences() != null && !parameters.stopSequences().isEmpty()) {
            throw new UnsupportedFeatureException("'stopSequences' parameter is not supported by OpenAI Responses API");
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

        private HttpClientBuilder httpClientBuilder;
        private String baseUrl;
        private String apiKey;
        private String organizationId;
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
        private String reasoningEffort;
        private String reasoningSummary;
        private String textVerbosity;
        private Boolean store;
        private Boolean strictTools;
        private Boolean strictJsonSchema;
        private ResponseFormat responseFormat;
        private List<ToolSpecification> toolSpecifications;
        private List<Map<String, Object>> serverTools;
        private ToolChoice toolChoice;
        private Boolean logRequests;
        private Boolean logResponses;
        private List<ChatModelListener> listeners;
        private ChatRequestParameters defaultRequestParameters;

        public Builder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder organizationId(String organizationId) {
            this.organizationId = organizationId;
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

        public Builder reasoningEffort(String reasoningEffort) {
            this.reasoningEffort = reasoningEffort;
            return this;
        }

        public Builder reasoningSummary(String reasoningSummary) {
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

        public Builder strictTools(Boolean strictTools) {
            this.strictTools = strictTools;
            return this;
        }

        public Builder strictJsonSchema(Boolean strictJsonSchema) {
            this.strictJsonSchema = strictJsonSchema;
            return this;
        }

        public Builder responseFormat(ResponseFormat responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        public Builder toolSpecifications(List<ToolSpecification> toolSpecifications) {
            this.toolSpecifications = toolSpecifications;
            return this;
        }

        public Builder toolSpecifications(ToolSpecification... toolSpecifications) {
            return toolSpecifications(asList(toolSpecifications));
        }

        public Builder serverTools(List<Map<String, Object>> serverTools) {
            this.serverTools = serverTools;
            return this;
        }

        public Builder toolChoice(ToolChoice toolChoice) {
            this.toolChoice = toolChoice;
            return this;
        }

        public Builder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public Builder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public Builder listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public Builder listeners(ChatModelListener... listeners) {
            return listeners(asList(listeners));
        }

        public Builder defaultRequestParameters(ChatRequestParameters parameters) {
            this.defaultRequestParameters = parameters;
            return this;
        }

        public OpenAiResponsesChatModel build() {
            return new OpenAiResponsesChatModel(this);
        }
    }
}
