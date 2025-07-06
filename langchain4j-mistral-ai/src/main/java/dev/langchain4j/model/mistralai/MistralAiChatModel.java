package dev.langchain4j.model.mistralai;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.ModelProvider.MISTRAL_AI;
import static dev.langchain4j.model.mistralai.InternalMistralAIHelper.createMistralAiRequest;
import static dev.langchain4j.model.mistralai.InternalMistralAIHelper.validate;
import static dev.langchain4j.model.mistralai.internal.mapper.MistralAiMapper.*;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.mistralai.internal.api.MistralAiChatCompletionRequest;
import dev.langchain4j.model.mistralai.internal.api.MistralAiChatCompletionResponse;
import dev.langchain4j.model.mistralai.internal.client.MistralAiClient;
import dev.langchain4j.model.mistralai.spi.MistralAiChatModelBuilderFactory;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents a Mistral AI Chat Model with a chat completion interface, such as open-mistral-7b and open-mixtral-8x7b
 * This model allows generating chat completion of a sync way based on a list of chat messages.
 * You can find description of parameters
 * <a href="https://docs.mistral.ai/api/#operation/createChatCompletion">here</a>.
 */
public class MistralAiChatModel implements ChatModel {

    private final MistralAiClient client;
    private final Boolean safePrompt;
    private final Integer randomSeed;
    private final Integer maxRetries;
    private final List<ChatModelListener> listeners;
    private final Set<Capability> supportedCapabilities;
    private final ChatRequestParameters defaultRequestParameters;

    public MistralAiChatModel(MistralAiChatModelBuilder builder) {
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
        this.maxRetries = getOrDefault(builder.maxRetries, 2);
        this.listeners = copy(builder.listeners);
        this.supportedCapabilities = copy(builder.supportedCapabilities);
        this.defaultRequestParameters = initDefaultRequestParameters(builder);
    }

    private ChatRequestParameters initDefaultRequestParameters(MistralAiChatModelBuilder builder) {
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
     * Constructs a MistralAiChatModel with the specified parameters.
     * @deprecated Please use {@link MistralAiChatModel#builder()} instead.
     *
     * @param httpClientBuilder the HTTP client builder to use for creating the HTTP client
     * @param baseUrl the base URL of the Mistral AI API. It uses the default value if not specified
     * @param apiKey the API key for authentication
     * @param modelName the name of the Mistral AI model to use
     * @param temperature the temperature parameter for generating chat responses
     * @param topP the top-p parameter for generating chat responses
     * @param maxTokens the maximum number of new tokens to generate in a chat response
     * @param safePrompt a flag indicating whether to use a safe prompt for generating chat responses
     * @param randomSeed the random seed for generating chat responses
     * @param responseFormat the response format for generating chat responses.
     * <p>
     * Current values supported are "text" and "json_object".
     * @param timeout the timeout duration for API requests
     * <p>
     * The default value is 60 seconds
     * @param logRequests a flag indicating whether to log API requests
     * @param logResponses a flag indicating whether to log API responses
     * @param maxRetries the maximum number of retries for API requests. It uses the default value 3 if not specified
     * @param supportedCapabilities the set of capabilities supported by this model
     */
    @Deprecated(forRemoval = true)
    public MistralAiChatModel(
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
            Duration timeout,
            Boolean logRequests,
            Boolean logResponses,
            Integer maxRetries,
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
        this.maxRetries = getOrDefault(maxRetries, 2);
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
     * Constructs a MistralAiChatModel with the specified parameters.
     * @deprecated Please use {@link MistralAiChatModel#builder()} instead.
     *
     * @param baseUrl the base URL of the Mistral AI API. It uses the default value if not specified
     * @param apiKey the API key for authentication
     * @param modelName the name of the Mistral AI model to use
     * @param temperature the temperature parameter for generating chat responses
     * @param topP the top-p parameter for generating chat responses
     * @param maxTokens the maximum number of new tokens to generate in a chat response
     * @param safePrompt a flag indicating whether to use a safe prompt for generating chat responses
     * @param randomSeed the random seed for generating chat responses
     * @param responseFormat the response format for generating chat responses.
     * <p>
     * Current values supported are "text" and "json_object".
     * @param timeout the timeout duration for API requests
     * <p>
     * The default value is 60 seconds
     * @param logRequests a flag indicating whether to log API requests
     * @param logResponses a flag indicating whether to log API responses
     * @param maxRetries the maximum number of retries for API requests. It uses the default value 3 if not specified
     * @param supportedCapabilities the set of capabilities supported by this model
     */
    @Deprecated(forRemoval = true)
    public MistralAiChatModel(
            String baseUrl,
            String apiKey,
            String modelName,
            Double temperature,
            Double topP,
            Integer maxTokens,
            Boolean safePrompt,
            Integer randomSeed,
            ResponseFormat responseFormat,
            Duration timeout,
            Boolean logRequests,
            Boolean logResponses,
            Integer maxRetries,
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
                timeout,
                logRequests,
                logResponses,
                maxRetries,
                supportedCapabilities);
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        validate(chatRequest.parameters());

        MistralAiChatCompletionRequest request = createMistralAiRequest(chatRequest, safePrompt, randomSeed, false);

        MistralAiChatCompletionResponse mistralAiResponse =
                withRetryMappingExceptions(() -> client.chatCompletion(request), maxRetries);

         return ChatResponse.builder()
                .aiMessage(aiMessageFrom(mistralAiResponse))
                .metadata(ChatResponseMetadata.builder()
                        .id(mistralAiResponse.getId())
                        .modelName(mistralAiResponse.getModel())
                        .tokenUsage(tokenUsageFrom(mistralAiResponse.getUsage()))
                        .finishReason(finishReasonFrom(mistralAiResponse.getChoices().get(0).getFinishReason()))
                        .build())
                .build();
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

    public static MistralAiChatModelBuilder builder() {
        for (MistralAiChatModelBuilderFactory factory : loadFactories(MistralAiChatModelBuilderFactory.class)) {
            return factory.get();
        }
        return new MistralAiChatModelBuilder();
    }

    public static class MistralAiChatModelBuilder {

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
        private Integer maxRetries;
        private List<ChatModelListener> listeners;
        private Set<Capability> supportedCapabilities;
        private ChatRequestParameters defaultRequestParameters;

        public MistralAiChatModelBuilder() {}

        public MistralAiChatModelBuilder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        public MistralAiChatModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public MistralAiChatModelBuilder modelName(MistralAiChatModelName modelName) {
            this.modelName = modelName.toString();
            return this;
        }

        public MistralAiChatModelBuilder responseFormat(ResponseFormat responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        /**
         * @param baseUrl the base URL of the Mistral AI API. It uses the default value if not specified
         * @return {@code this}.
         */
        public MistralAiChatModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * @param apiKey the API key for authentication
         * @return {@code this}.
         */
        public MistralAiChatModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * @param temperature the temperature parameter for generating chat responses
         * @return {@code this}.
         */
        public MistralAiChatModelBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        /**
         * @param topP the top-p parameter for generating chat responses
         * @return {@code this}.
         */
        public MistralAiChatModelBuilder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        /**
         * @param maxTokens the maximum number of new tokens to generate in a chat response
         * @return {@code this}.
         */
        public MistralAiChatModelBuilder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        /**
         * @param safePrompt a flag indicating whether to use a safe prompt for generating chat responses
         * @return {@code this}.
         */
        public MistralAiChatModelBuilder safePrompt(Boolean safePrompt) {
            this.safePrompt = safePrompt;
            return this;
        }

        /**
         * @param randomSeed the random seed for generating chat responses
         * @return {@code this}.
         */
        public MistralAiChatModelBuilder randomSeed(Integer randomSeed) {
            this.randomSeed = randomSeed;
            return this;
        }

        /**
         * @param timeout the timeout duration for API requests
         * <p>
         * The default value is 60 seconds
         * @return {@code this}.
         */
        public MistralAiChatModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * @param logRequests a flag indicating whether to log API requests
         * @return {@code this}.
         */
        public MistralAiChatModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        /**
         * @param logResponses a flag indicating whether to log API responses
         * @return {@code this}.
         */
        public MistralAiChatModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        /**
         * @param maxRetries
         * @return {@code this}.
         */
        public MistralAiChatModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public MistralAiChatModelBuilder supportedCapabilities(Capability... supportedCapabilities) {
            this.supportedCapabilities = Arrays.stream(supportedCapabilities).collect(Collectors.toSet());
            return this;
        }

        public MistralAiChatModelBuilder supportedCapabilities(Set<Capability> supportedCapabilities) {
            this.supportedCapabilities = Set.copyOf(supportedCapabilities);
            return this;
        }

        public MistralAiChatModelBuilder stopSequences(List<String> stopSequences) {
            this.stopSequences = stopSequences;
            return this;
        }

        public MistralAiChatModelBuilder presencePenalty(Double presencePenalty) {
            this.presencePenalty = presencePenalty;
            return this;
        }

        public MistralAiChatModelBuilder frequencyPenalty(Double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
            return this;
        }

        public MistralAiChatModelBuilder listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public MistralAiChatModelBuilder defaultRequestParameters(ChatRequestParameters parameters) {
            this.defaultRequestParameters = parameters;
            return this;
        }

        public MistralAiChatModel build() {
            return new MistralAiChatModel(this);
        }
    }
}
