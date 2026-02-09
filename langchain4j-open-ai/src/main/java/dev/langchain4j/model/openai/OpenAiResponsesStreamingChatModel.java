package dev.langchain4j.model.openai;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.copyIfNotNull;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.Arrays.asList;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialThinkingContext;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.PartialToolCallContext;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@Experimental
public class OpenAiResponsesStreamingChatModel implements StreamingChatModel {

    private final OpenAiResponsesClient client;
    private final String modelName;
    private final Double temperature;
    private final Double topP;
    private final Integer maxOutputTokens;
    private final Integer maxToolCalls;
    private final Boolean parallelToolCalls;
    private final String previousResponseId;
    private final Integer topLogprobs;
    private final String truncation;
    private final List<String> include;
    private final String serviceTier;
    private final String safetyIdentifier;
    private final String promptCacheKey;
    private final String promptCacheRetention;
    private final String reasoningEffort;
    private final String textVerbosity;
    private final Boolean streamIncludeObfuscation;
    private final Boolean store;
    private final Boolean strict;
    private final List<ChatModelListener> listeners;
    private final ChatRequestParameters defaultRequestParameters;
    private final AtomicReference<String> lastResponseId = new AtomicReference<>();

    private OpenAiResponsesStreamingChatModel(Builder builder) {
        this.client = OpenAiResponsesClient.builder()
                .httpClientBuilder(builder.httpClientBuilder)
                .baseUrl(builder.baseUrl)
                .apiKey(builder.apiKey)
                .organizationId(builder.organizationId)
                .build();

        ChatRequestParameters commonParameters;
        if (builder.defaultRequestParameters != null) {
            commonParameters = builder.defaultRequestParameters;
        } else {
            commonParameters = DefaultChatRequestParameters.EMPTY;
        }

        this.modelName = ensureNotNull(getOrDefault(builder.modelName, commonParameters.modelName()), "modelName");
        this.temperature = getOrDefault(builder.temperature, commonParameters.temperature());
        this.topP = getOrDefault(builder.topP, commonParameters.topP());
        this.maxOutputTokens = getOrDefault(builder.maxOutputTokens, commonParameters.maxOutputTokens());
        this.maxToolCalls = builder.maxToolCalls;
        this.parallelToolCalls = builder.parallelToolCalls;
        this.previousResponseId = builder.previousResponseId;
        this.topLogprobs = builder.topLogprobs;
        this.truncation = builder.truncation;
        this.include = copyIfNotNull(builder.include);
        this.serviceTier = builder.serviceTier;
        this.safetyIdentifier = builder.safetyIdentifier;
        this.promptCacheKey = builder.promptCacheKey;
        this.promptCacheRetention = builder.promptCacheRetention;
        this.reasoningEffort = builder.reasoningEffort;
        this.textVerbosity = builder.textVerbosity;
        this.streamIncludeObfuscation = builder.streamIncludeObfuscation;
        // Default to false to avoid persisting data unless explicitly requested.
        this.store = getOrDefault(builder.store, false);
        // Default to false to avoid rejecting outputs unless explicitly enabled by the user.
        this.strict = getOrDefault(builder.strict, false);
        this.listeners = copy(builder.listeners);
        this.defaultRequestParameters = DefaultChatRequestParameters.builder()
                .modelName(modelName)
                .temperature(temperature)
                .topP(topP)
                .maxOutputTokens(maxOutputTokens)
                .toolSpecifications(commonParameters.toolSpecifications())
                .toolChoice(commonParameters.toolChoice())
                .responseFormat(getOrDefault(builder.responseFormat, commonParameters.responseFormat()))
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        String effectivePreviousResponseId = previousResponseId;
        if (effectivePreviousResponseId == null
                && Boolean.TRUE.equals(store)
                && containsToolExecutionResult(chatRequest)) {
            effectivePreviousResponseId = lastResponseId.get();
        }

        OpenAiResponsesConfig config = new OpenAiResponsesConfig(
                modelName,
                temperature,
                topP,
                maxOutputTokens,
                maxToolCalls,
                parallelToolCalls,
                effectivePreviousResponseId,
                topLogprobs,
                truncation,
                include,
                serviceTier,
                safetyIdentifier,
                promptCacheKey,
                promptCacheRetention,
                reasoningEffort,
                textVerbosity,
                streamIncludeObfuscation,
                store,
                strict);

        client.streamingChat(chatRequest, config, wrapHandler(handler));
    }

    private boolean containsToolExecutionResult(ChatRequest chatRequest) {
        return chatRequest.messages().stream().anyMatch(ToolExecutionResultMessage.class::isInstance);
    }

    private StreamingChatResponseHandler wrapHandler(StreamingChatResponseHandler handler) {
        return new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                handler.onPartialResponse(partialResponse);
            }

            @Override
            public void onPartialResponse(PartialResponse partialResponse, PartialResponseContext context) {
                handler.onPartialResponse(partialResponse, context);
            }

            @Override
            public void onPartialThinking(PartialThinking partialThinking) {
                handler.onPartialThinking(partialThinking);
            }

            @Override
            public void onPartialThinking(PartialThinking partialThinking, PartialThinkingContext context) {
                handler.onPartialThinking(partialThinking, context);
            }

            @Override
            public void onPartialToolCall(PartialToolCall partialToolCall) {
                handler.onPartialToolCall(partialToolCall);
            }

            @Override
            public void onPartialToolCall(PartialToolCall partialToolCall, PartialToolCallContext context) {
                handler.onPartialToolCall(partialToolCall, context);
            }

            @Override
            public void onCompleteToolCall(CompleteToolCall completeToolCall) {
                handler.onCompleteToolCall(completeToolCall);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                if (completeResponse != null && completeResponse.metadata() != null) {
                    String responseId = completeResponse.metadata().id();
                    if (responseId != null && !responseId.isBlank()) {
                        lastResponseId.set(responseId);
                    }
                }
                handler.onCompleteResponse(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                handler.onError(error);
            }
        };
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
        private String textVerbosity;
        private Boolean streamIncludeObfuscation;
        private Boolean store;
        private Boolean strict;
        private List<ChatModelListener> listeners;
        private ChatRequestParameters defaultRequestParameters;
        private ResponseFormat responseFormat;

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

        public Builder textVerbosity(String textVerbosity) {
            this.textVerbosity = textVerbosity;
            return this;
        }

        public Builder streamIncludeObfuscation(Boolean streamIncludeObfuscation) {
            this.streamIncludeObfuscation = streamIncludeObfuscation;
            return this;
        }

        public Builder store(Boolean store) {
            this.store = store;
            return this;
        }

        public Builder strict(Boolean strict) {
            this.strict = strict;
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

        public Builder responseFormat(ResponseFormat responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        public OpenAiResponsesStreamingChatModel build() {
            return new OpenAiResponsesStreamingChatModel(this);
        }
    }
}
