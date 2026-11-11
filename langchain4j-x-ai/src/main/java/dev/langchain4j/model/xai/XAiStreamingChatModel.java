package dev.langchain4j.model.xai;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;
import static java.util.Arrays.asList;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Flow.Publisher;
import java.util.function.Supplier;

/**
 * A {@link StreamingChatModel} for xAI's Grok models, backed by the
 * <a href="https://docs.x.ai/developers/model-capabilities/text/generate-text">xAI Responses API</a>.
 */
public class XAiStreamingChatModel implements StreamingChatModel {

    private final XAiClient client;
    private final XAiChatRequestParameters defaultRequestParameters;
    private final List<ChatModelListener> listeners;

    private XAiStreamingChatModel(Builder builder) {
        this.client = XAiClient.builder()
                .httpClientBuilder(builder.httpClientBuilder)
                .baseUrl(builder.baseUrl)
                .apiKey(builder.apiKey)
                .connectTimeout(getOrDefault(builder.timeout, ofSeconds(15)))
                // reasoning models can think for a long time before answering
                .readTimeout(getOrDefault(builder.timeout, ofMinutes(10)))
                .logRequests(builder.logRequests)
                .logResponses(builder.logResponses)
                .streamingBufferSize(builder.streamingBufferSize)
                .customHeaders(builder.customHeadersSupplier)
                .build();

        ChatRequestParameters commonParameters;
        if (builder.defaultRequestParameters != null) {
            validate(builder.defaultRequestParameters);
            commonParameters = builder.defaultRequestParameters;
        } else {
            commonParameters = DefaultChatRequestParameters.EMPTY;
        }

        XAiChatRequestParameters xAiParameters =
                commonParameters instanceof XAiChatRequestParameters p ? p : XAiChatRequestParameters.EMPTY;

        this.defaultRequestParameters = XAiChatRequestParameters.builder()
                .modelName(ensureNotNull(getOrDefault(builder.modelName, commonParameters.modelName()), "modelName"))
                .temperature(getOrDefault(builder.temperature, commonParameters.temperature()))
                .topP(getOrDefault(builder.topP, commonParameters.topP()))
                .topK(getOrDefault(builder.topK, commonParameters.topK()))
                .maxOutputTokens(getOrDefault(builder.maxOutputTokens, commonParameters.maxOutputTokens()))
                .toolSpecifications(getOrDefault(builder.toolSpecifications, commonParameters.toolSpecifications()))
                .toolChoice(getOrDefault(builder.toolChoice, commonParameters.toolChoice()))
                .responseFormat(getOrDefault(builder.responseFormat, commonParameters.responseFormat()))
                .previousResponseId(getOrDefault(builder.previousResponseId, xAiParameters.previousResponseId()))
                .store(getOrDefault(builder.store, xAiParameters.store()))
                .reasoningEffort(getOrDefault(builder.reasoningEffort, xAiParameters.reasoningEffort()))
                .include(getOrDefault(builder.include, xAiParameters.include()))
                .maxToolCalls(getOrDefault(builder.maxToolCalls, xAiParameters.maxToolCalls()))
                .parallelToolCalls(getOrDefault(builder.parallelToolCalls, xAiParameters.parallelToolCalls()))
                .promptCacheKey(getOrDefault(builder.promptCacheKey, xAiParameters.promptCacheKey()))
                .user(getOrDefault(builder.user, xAiParameters.user()))
                .build();

        this.listeners = copy(builder.listeners);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        validate(chatRequest.parameters());
        // StreamingChatModel.chat() merges the request parameters onto defaultRequestParameters(), which are xAI ones
        XAiChatRequestParameters parameters = (XAiChatRequestParameters) chatRequest.parameters();
        client.streamingChat(chatRequest, parameters, handler);
    }

    @Override
    public Publisher<ChatModelStreamingEvent> doChat(ChatRequest chatRequest) {
        validate(chatRequest.parameters());
        XAiChatRequestParameters parameters = (XAiChatRequestParameters) chatRequest.parameters();
        return client.streamingChatPublisher(chatRequest, parameters);
    }

    private static void validate(ChatRequestParameters parameters) {
        if (parameters.frequencyPenalty() != null) {
            throw new UnsupportedFeatureException("'frequencyPenalty' parameter is not supported by xAI");
        }
        if (parameters.presencePenalty() != null) {
            throw new UnsupportedFeatureException("'presencePenalty' parameter is not supported by xAI");
        }
        if (parameters.stopSequences() != null && !parameters.stopSequences().isEmpty()) {
            throw new UnsupportedFeatureException("'stopSequences' parameter is not supported by xAI");
        }
    }

    @Override
    public XAiChatRequestParameters defaultRequestParameters() {
        return defaultRequestParameters;
    }

    @Override
    public List<ChatModelListener> listeners() {
        return listeners;
    }

    @Override
    public ModelProvider provider() {
        return ModelProvider.X_AI;
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return Set.of(Capability.RESPONSE_FORMAT_JSON_SCHEMA);
    }

    public static class Builder {

        private HttpClientBuilder httpClientBuilder;
        private String baseUrl;
        private String apiKey;
        private String modelName;
        private Double temperature;
        private Double topP;
        private Integer topK;
        private Integer maxOutputTokens;
        private List<ToolSpecification> toolSpecifications;
        private ToolChoice toolChoice;
        private ResponseFormat responseFormat;
        private String previousResponseId;
        private Boolean store;
        private String reasoningEffort;
        private List<String> include;
        private Integer maxToolCalls;
        private Boolean parallelToolCalls;
        private String promptCacheKey;
        private String user;
        private Duration timeout;
        private Integer streamingBufferSize;
        private Boolean logRequests;
        private Boolean logResponses;
        private List<ChatModelListener> listeners;
        private ChatRequestParameters defaultRequestParameters;
        private Supplier<Map<String, String>> customHeadersSupplier;

        public Builder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        /**
         * Defaults to {@code https://api.x.ai/v1}.
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder modelName(XAiChatModelName modelName) {
            this.modelName = modelName.toString();
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

        public Builder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        public Builder maxOutputTokens(Integer maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
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

        public Builder previousResponseId(String previousResponseId) {
            this.previousResponseId = previousResponseId;
            return this;
        }

        public Builder store(Boolean store) {
            this.store = store;
            return this;
        }

        public Builder reasoningEffort(String reasoningEffort) {
            this.reasoningEffort = reasoningEffort;
            return this;
        }

        public Builder include(List<String> include) {
            this.include = include;
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

        public Builder promptCacheKey(String promptCacheKey) {
            this.promptCacheKey = promptCacheKey;
            return this;
        }

        public Builder user(String user) {
            this.user = user;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * The number of streaming events buffered when using the {@link Publisher}-based
         * {@link StreamingChatModel#chat(ChatRequest)}. Defaults to 16384.
         */
        public Builder streamingBufferSize(Integer streamingBufferSize) {
            this.streamingBufferSize = streamingBufferSize;
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

        public Builder defaultRequestParameters(ChatRequestParameters defaultRequestParameters) {
            this.defaultRequestParameters = defaultRequestParameters;
            return this;
        }

        /**
         * Sets custom HTTP headers to be added to each HTTP request.
         */
        public Builder customHeaders(Map<String, String> customHeaders) {
            this.customHeadersSupplier = () -> customHeaders;
            return this;
        }

        /**
         * Sets a supplier for custom HTTP headers, called before each HTTP request.
         */
        public Builder customHeaders(Supplier<Map<String, String>> customHeadersSupplier) {
            this.customHeadersSupplier = customHeadersSupplier;
            return this;
        }

        public XAiStreamingChatModel build() {
            return new XAiStreamingChatModel(this);
        }
    }
}
