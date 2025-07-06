package dev.langchain4j.model.mistralai;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.model.ModelProvider.MISTRAL_AI;
import static dev.langchain4j.model.mistralai.InternalMistralAIHelper.createMistralAiRequest;
import static dev.langchain4j.model.mistralai.InternalMistralAIHelper.validate;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.mistralai.internal.api.MistralAiChatCompletionRequest;
import dev.langchain4j.model.mistralai.internal.client.MistralAiClient;
import dev.langchain4j.model.mistralai.spi.MistralAiStreamingChatModelBuilderFactory;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents a Mistral AI Chat Model with a chat completion interface, such as mistral-tiny and mistral-small.
 * The model's response is streamed token by token and should be handled with {@link StreamingResponseHandler}.
 * You can find description of parameters <a href="https://docs.mistral.ai/api/#operation/createChatCompletion">here</a>.
 */
public class MistralAiStreamingChatModel implements StreamingChatModel {

    private final MistralAiClient client;
    private final Boolean safePrompt;
    private final Integer randomSeed;
    private final List<ChatModelListener> listeners;
    private final Set<Capability> supportedCapabilities;
    private final ChatRequestParameters defaultRequestParameters;

    public MistralAiStreamingChatModel(MistralAiStreamingChatModelBuilder builder) {
        this.client = MistralAiClient.builder()
                .httpClientBuilder(builder.httpClientBuilder)
                .baseUrl(getOrDefault(builder.baseUrl, "https://api.mistral.ai/v1"))
                .apiKey(builder.apiKey)
                .timeout(builder.timeout)
                .logRequests(getOrDefault(builder.logRequests, false))
                .logResponses(getOrDefault(builder.logResponses, false))
                .build();

        this.safePrompt = builder.safePrompt;
        this.randomSeed = builder.randomSeed;
        this.listeners = copy(builder.listeners);
        this.supportedCapabilities = copy(builder.supportedCapabilities);
        this.defaultRequestParameters = initDefaultRequestParameters(builder);
    }

    private ChatRequestParameters initDefaultRequestParameters(MistralAiStreamingChatModelBuilder builder) {
        ChatRequestParameters commonParameters;
        if (builder.defaultRequestParameters != null) {
            validate(builder.defaultRequestParameters);
            commonParameters = builder.defaultRequestParameters;
        } else {
            commonParameters = DefaultChatRequestParameters.EMPTY;
        }

        return DefaultChatRequestParameters.builder()
                .modelName(getOrDefault(builder.modelName, commonParameters.modelName()))
                .temperature(getOrDefault(builder.temperature, commonParameters.temperature()))
                .topP(getOrDefault(builder.topP, commonParameters.topP()))
                .frequencyPenalty(getOrDefault(builder.frequencyPenalty, commonParameters.frequencyPenalty()))
                .presencePenalty(getOrDefault(builder.presencePenalty, commonParameters.presencePenalty()))
                .maxOutputTokens(getOrDefault(builder.maxTokens, commonParameters.maxOutputTokens()))
                .stopSequences(getOrDefault(builder.stopSequences, commonParameters.stopSequences()))
                .toolSpecifications(commonParameters.toolSpecifications())
                .toolChoice(commonParameters.toolChoice())
                .responseFormat(getOrDefault(builder.responseFormat, commonParameters.responseFormat()))
                .build();
    }

    /**
     * @deprecated please use {@link MistralAiStreamingChatModel#builder()} instead
     */
    @Deprecated(forRemoval = true)
    public MistralAiStreamingChatModel(
            HttpClientBuilder httpClientBuilder,
            String baseUrl,
            String apiKey,
            String modelName,
            Double temperature,
            Double topP,
            Integer maxTokens,
            Boolean safePrompt,
            Integer randomSeed,
            ResponseFormat responseFormat,
            Boolean logRequests,
            Boolean logResponses,
            Duration timeout,
            Set<Capability> supportedCapabilities) {
        this.client = MistralAiClient.builder()
                .httpClientBuilder(httpClientBuilder)
                .baseUrl(getOrDefault(baseUrl, "https://api.mistral.ai/v1"))
                .apiKey(apiKey)
                .timeout(timeout)
                .logRequests(getOrDefault(logRequests, false))
                .logResponses(getOrDefault(logResponses, false))
                .build();

        this.safePrompt = safePrompt;
        this.randomSeed = randomSeed;
        this.listeners = List.of();
        this.supportedCapabilities = getOrDefault(supportedCapabilities, Set.of());
        this.defaultRequestParameters = initDefaultRequestParameters(ensureNotBlank(modelName, "modelName"), temperature, topP, maxTokens, responseFormat);
    }

    private ChatRequestParameters initDefaultRequestParameters(String modelName,
                                                               Double temperature,
                                                               Double topP,
                                                               Integer maxTokens,
                                                               ResponseFormat responseFormat) {
        ChatRequestParameters commonParameters = DefaultChatRequestParameters.EMPTY;

        return DefaultChatRequestParameters.builder()
                .modelName(getOrDefault(modelName, commonParameters.modelName()))
                .temperature(getOrDefault(temperature, commonParameters.temperature()))
                .topP(getOrDefault(topP, commonParameters.topP()))
                .maxOutputTokens(getOrDefault(maxTokens, commonParameters.maxOutputTokens()))
                .responseFormat(getOrDefault(responseFormat, commonParameters.responseFormat()))
                .build();
    }

    /**
     * @deprecated please use {@link #MistralAiStreamingChatModel(MistralAiStreamingChatModelBuilder)} instead
     */
    @Deprecated(forRemoval = true)
    public MistralAiStreamingChatModel(
            String baseUrl,
            String apiKey,
            String modelName,
            Double temperature,
            Double topP,
            Integer maxTokens,
            Boolean safePrompt,
            Integer randomSeed,
            ResponseFormat responseFormat,
            Boolean logRequests,
            Boolean logResponses,
            Duration timeout,
            Set<Capability> supportedCapabilities) {
        this(
                null,
                baseUrl,
                apiKey,
                modelName,
                temperature,
                topP,
                maxTokens,
                safePrompt,
                randomSeed,
                responseFormat,
                logRequests,
                logResponses,
                timeout,
                supportedCapabilities);
    }

    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        ensureNotNull(handler, "handler");
        validate(chatRequest.parameters());

        MistralAiChatCompletionRequest request = createMistralAiRequest(chatRequest, safePrompt, randomSeed, true);
        client.streamingChatCompletion(request, handler);
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
        return MISTRAL_AI;
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return supportedCapabilities;
    }

    public static MistralAiStreamingChatModelBuilder builder() {
        for (MistralAiStreamingChatModelBuilderFactory factory :
                loadFactories(MistralAiStreamingChatModelBuilderFactory.class)) {
            return factory.get();
        }
        return new MistralAiStreamingChatModelBuilder();
    }

    public static class MistralAiStreamingChatModelBuilder {

        private HttpClientBuilder httpClientBuilder;
        private String baseUrl;
        private String apiKey;
        private String modelName;
        private Double temperature;
        private Double topP;
        private Integer maxTokens;
        private Boolean safePrompt;
        private Integer randomSeed;
        private ResponseFormat responseFormat;
        private List<String> stopSequences;
        private Double frequencyPenalty;
        private Double presencePenalty;
        private Duration timeout;
        private Boolean logRequests;
        private Boolean logResponses;
        private List<ChatModelListener> listeners;
        private Set<Capability> supportedCapabilities;
        private ChatRequestParameters defaultRequestParameters;

        public MistralAiStreamingChatModelBuilder() {}

        public MistralAiStreamingChatModelBuilder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        public MistralAiStreamingChatModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public MistralAiStreamingChatModelBuilder modelName(MistralAiChatModelName modelName) {
            this.modelName = modelName.toString();
            return this;
        }

        public MistralAiStreamingChatModelBuilder responseFormat(ResponseFormat responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        /**
         * @param baseUrl the base URL of the Mistral AI API. It uses the default value if not specified
         * @return {@code this}.
         */
        public MistralAiStreamingChatModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * @param apiKey the API key for authentication
         * @return {@code this}.
         */
        public MistralAiStreamingChatModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * @param temperature the temperature parameter for generating chat responses
         * @return {@code this}.
         */
        public MistralAiStreamingChatModelBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        /**
         * @param topP the top-p parameter for generating chat responses
         * @return {@code this}.
         */
        public MistralAiStreamingChatModelBuilder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        /**
         * @param maxTokens the maximum number of new tokens to generate in a chat response
         * @return {@code this}.
         */
        public MistralAiStreamingChatModelBuilder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        /**
         * @param safePrompt a flag indicating whether to use a safe prompt for generating chat responses
         * @return {@code this}.
         */
        public MistralAiStreamingChatModelBuilder safePrompt(Boolean safePrompt) {
            this.safePrompt = safePrompt;
            return this;
        }

        /**
         * @param randomSeed the random seed for generating chat responses
         * @return {@code this}.
         */
        public MistralAiStreamingChatModelBuilder randomSeed(Integer randomSeed) {
            this.randomSeed = randomSeed;
            return this;
        }

        /**
         * @param timeout the timeout duration for API requests
         * <p>
         * The default value is 60 seconds
         * @return {@code this}.
         */
        public MistralAiStreamingChatModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * @param logRequests a flag indicating whether to log API requests
         * @return {@code this}.
         */
        public MistralAiStreamingChatModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        /**
         * @param logResponses a flag indicating whether to log API responses
         * @return {@code this}.
         */
        public MistralAiStreamingChatModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public MistralAiStreamingChatModelBuilder supportedCapabilities(Capability... supportedCapabilities) {
            this.supportedCapabilities = Arrays.stream(supportedCapabilities).collect(Collectors.toSet());
            return this;
        }

        public MistralAiStreamingChatModelBuilder supportedCapabilities(Set<Capability> supportedCapabilities) {
            this.supportedCapabilities = Set.copyOf(supportedCapabilities);
            return this;
        }

        public MistralAiStreamingChatModelBuilder stopSequences(List<String> stopSequences) {
            this.stopSequences = stopSequences;
            return this;
        }

        public MistralAiStreamingChatModelBuilder presencePenalty(Double presencePenalty) {
            this.presencePenalty = presencePenalty;
            return this;
        }

        public MistralAiStreamingChatModelBuilder frequencyPenalty(Double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
            return this;
        }

        public MistralAiStreamingChatModelBuilder listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public MistralAiStreamingChatModelBuilder defaultRequestParameters(ChatRequestParameters parameters) {
            this.defaultRequestParameters = parameters;
            return this;
        }

        public MistralAiStreamingChatModel build() {
            return new MistralAiStreamingChatModel(this);
        }
    }
}
