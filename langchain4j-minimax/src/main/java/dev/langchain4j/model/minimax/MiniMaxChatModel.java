package dev.langchain4j.model.minimax;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.minimax.MiniMaxUtils.DEFAULT_MINIMAX_URL;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.util.Arrays.asList;

import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.minimax.spi.MiniMaxChatModelBuilderFactory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.slf4j.Logger;

/**
 * Represents a MiniMax language model with a chat completion interface.
 * MiniMax provides an OpenAI-compatible API, so this module delegates to the OpenAI client
 * with MiniMax-specific defaults (base URL, model names, temperature clamping).
 *
 * @see <a href="https://platform.minimax.io/docs/api-reference/text-openai-api">MiniMax Chat API</a>
 */
public class MiniMaxChatModel implements ChatModel {

    private final OpenAiChatModel delegate;

    public MiniMaxChatModel(MiniMaxChatModelBuilder builder) {
        this.delegate = OpenAiChatModel.builder()
                .httpClientBuilder(builder.httpClientBuilder)
                .baseUrl(getOrDefault(builder.baseUrl, DEFAULT_MINIMAX_URL))
                .apiKey(builder.apiKey)
                .modelName(getOrDefault(builder.modelName, MiniMaxChatModelName.MINIMAX_M2_7.toString()))
                .temperature(MiniMaxUtils.clampTemperature(builder.temperature))
                .topP(builder.topP)
                .stop(builder.stop)
                .maxTokens(builder.maxTokens)
                .maxCompletionTokens(builder.maxCompletionTokens)
                .presencePenalty(builder.presencePenalty)
                .frequencyPenalty(builder.frequencyPenalty)
                .responseFormat(builder.responseFormat)
                .supportedCapabilities(builder.supportedCapabilities)
                .strictJsonSchema(builder.strictJsonSchema)
                .strictTools(builder.strictTools)
                .timeout(builder.timeout)
                .maxRetries(builder.maxRetries)
                .logRequests(builder.logRequests)
                .logResponses(builder.logResponses)
                .logger(builder.logger)
                .customHeaders(builder.customHeadersSupplier)
                .customQueryParams(builder.customQueryParams)
                .customParameters(builder.customParameters)
                .listeners(builder.listeners)
                .build();
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return delegate.defaultRequestParameters();
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return delegate.supportedCapabilities();
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        return delegate.doChat(chatRequest);
    }

    @Override
    public List<ChatModelListener> listeners() {
        return delegate.listeners();
    }

    @Override
    public ModelProvider provider() {
        return ModelProvider.OTHER;
    }

    public static MiniMaxChatModelBuilder builder() {
        for (MiniMaxChatModelBuilderFactory factory : loadFactories(MiniMaxChatModelBuilderFactory.class)) {
            return factory.get();
        }
        return new MiniMaxChatModelBuilder();
    }

    public static class MiniMaxChatModelBuilder {

        HttpClientBuilder httpClientBuilder;
        String baseUrl;
        String apiKey;
        String modelName;
        Double temperature;
        Double topP;
        List<String> stop;
        Integer maxTokens;
        Integer maxCompletionTokens;
        Double presencePenalty;
        Double frequencyPenalty;
        ResponseFormat responseFormat;
        Set<Capability> supportedCapabilities;
        Boolean strictJsonSchema;
        Boolean strictTools;
        Duration timeout;
        Integer maxRetries;
        Boolean logRequests;
        Boolean logResponses;
        Logger logger;
        Supplier<Map<String, String>> customHeadersSupplier;
        Map<String, String> customQueryParams;
        Map<String, Object> customParameters;
        List<ChatModelListener> listeners;

        public MiniMaxChatModelBuilder() {
            // This is public so it can be extended
        }

        public MiniMaxChatModelBuilder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        public MiniMaxChatModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public MiniMaxChatModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public MiniMaxChatModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public MiniMaxChatModelBuilder modelName(MiniMaxChatModelName modelName) {
            this.modelName = modelName.toString();
            return this;
        }

        public MiniMaxChatModelBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public MiniMaxChatModelBuilder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public MiniMaxChatModelBuilder stop(List<String> stop) {
            this.stop = stop;
            return this;
        }

        public MiniMaxChatModelBuilder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public MiniMaxChatModelBuilder maxCompletionTokens(Integer maxCompletionTokens) {
            this.maxCompletionTokens = maxCompletionTokens;
            return this;
        }

        public MiniMaxChatModelBuilder presencePenalty(Double presencePenalty) {
            this.presencePenalty = presencePenalty;
            return this;
        }

        public MiniMaxChatModelBuilder frequencyPenalty(Double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
            return this;
        }

        public MiniMaxChatModelBuilder responseFormat(ResponseFormat responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        public MiniMaxChatModelBuilder supportedCapabilities(Set<Capability> supportedCapabilities) {
            this.supportedCapabilities = supportedCapabilities;
            return this;
        }

        public MiniMaxChatModelBuilder supportedCapabilities(Capability... supportedCapabilities) {
            return supportedCapabilities(new java.util.HashSet<>(asList(supportedCapabilities)));
        }

        public MiniMaxChatModelBuilder strictJsonSchema(Boolean strictJsonSchema) {
            this.strictJsonSchema = strictJsonSchema;
            return this;
        }

        public MiniMaxChatModelBuilder strictTools(Boolean strictTools) {
            this.strictTools = strictTools;
            return this;
        }

        public MiniMaxChatModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public MiniMaxChatModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public MiniMaxChatModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public MiniMaxChatModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public MiniMaxChatModelBuilder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public MiniMaxChatModelBuilder customHeaders(Map<String, String> customHeaders) {
            this.customHeadersSupplier = () -> customHeaders;
            return this;
        }

        public MiniMaxChatModelBuilder customHeaders(Supplier<Map<String, String>> customHeadersSupplier) {
            this.customHeadersSupplier = customHeadersSupplier;
            return this;
        }

        public MiniMaxChatModelBuilder customQueryParams(Map<String, String> customQueryParams) {
            this.customQueryParams = customQueryParams;
            return this;
        }

        public MiniMaxChatModelBuilder customParameters(Map<String, Object> customParameters) {
            this.customParameters = customParameters;
            return this;
        }

        public MiniMaxChatModelBuilder listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public MiniMaxChatModelBuilder listeners(ChatModelListener... listeners) {
            return listeners(asList(listeners));
        }

        public MiniMaxChatModel build() {
            return new MiniMaxChatModel(this);
        }
    }
}
