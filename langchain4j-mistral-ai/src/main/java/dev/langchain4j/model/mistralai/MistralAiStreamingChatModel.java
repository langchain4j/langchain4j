package dev.langchain4j.model.mistralai;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.model.ModelProvider.MISTRAL_AI;
import static dev.langchain4j.model.chat.request.ToolChoice.REQUIRED;
import static dev.langchain4j.model.mistralai.internal.mapper.MistralAiMapper.*;
import static dev.langchain4j.model.mistralai.internal.mapper.MistralAiMapper.toMistralAiTools;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.util.Collections.singletonList;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.internal.ChatRequestValidationUtils;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.mistralai.internal.api.MistralAiChatCompletionRequest;
import dev.langchain4j.model.mistralai.internal.api.MistralAiResponseFormatType;
import dev.langchain4j.model.mistralai.internal.api.MistralAiToolChoiceName;
import dev.langchain4j.model.mistralai.internal.client.MistralAiClient;
import dev.langchain4j.model.mistralai.spi.MistralAiStreamingChatModelBuilderFactory;
import dev.langchain4j.model.output.Response;
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
    private final String modelName;
    private final Double temperature;
    private final Double topP;
    private final Integer maxTokens;
    private final Boolean safePrompt;
    private final Integer randomSeed;
    private final ResponseFormat responseFormat;

    private final Set<Capability> supportedCapabilities;

    /**
     * Constructs a MistralAiStreamingChatModel with the specified parameters.
     *
     * @param httpClientBuilder the HTTP client builder to use for creating the HTTP client
     * @param baseUrl      the base URL of the Mistral AI API. It uses the default value if not specified
     * @param apiKey       the API key for authentication
     * @param modelName    the name of the Mistral AI model to use
     * @param temperature  the temperature parameter for generating chat responses
     * @param topP         the top-p parameter for generating chat responses
     * @param maxTokens    the maximum number of new tokens to generate in a chat response
     * @param safePrompt   a flag indicating whether to use a safe prompt for generating chat responses
     * @param randomSeed   the random seed for generating chat responses
     *                     (if not specified, a random number is used)
     * @param responseFormat the response format for generating chat responses. Current values supported are "text" and "json_object".
     * @param logRequests  a flag indicating whether to log raw HTTP requests
     * @param logResponses a flag indicating whether to log raw HTTP responses
     * @param timeout      the timeout duration for API requests
     * @param supportedCapabilities the set of capabilities supported by this model
     */
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
                .timeout(getOrDefault(timeout, Duration.ofSeconds(60)))
                .logRequests(getOrDefault(logRequests, false))
                .logResponses(getOrDefault(logResponses, false))
                .build();
        this.modelName = ensureNotBlank(modelName, "modelName");
        this.temperature = temperature;
        this.topP = topP;
        this.maxTokens = maxTokens;
        this.safePrompt = safePrompt;
        this.randomSeed = randomSeed;
        this.responseFormat = responseFormat;
        this.supportedCapabilities = getOrDefault(supportedCapabilities, Set.of());
    }

    /**
     * Constructs a MistralAiStreamingChatModel with the specified parameters.
     * @deprecated please use {@link #MistralAiStreamingChatModel(HttpClientBuilder, String, String, String, Double, Double, Integer, Boolean, Integer, ResponseFormat, Boolean, Boolean, Duration, Set)} instead
     *
     * @param baseUrl      the base URL of the Mistral AI API. It uses the default value if not specified
     * @param apiKey       the API key for authentication
     * @param modelName    the name of the Mistral AI model to use
     * @param temperature  the temperature parameter for generating chat responses
     * @param topP         the top-p parameter for generating chat responses
     * @param maxTokens    the maximum number of new tokens to generate in a chat response
     * @param safePrompt   a flag indicating whether to use a safe prompt for generating chat responses
     * @param randomSeed   the random seed for generating chat responses
     *                     (if not specified, a random number is used)
     * @param responseFormat the response format for generating chat responses. Current values supported are "text" and "json_object".
     * @param logRequests  a flag indicating whether to log raw HTTP requests
     * @param logResponses a flag indicating whether to log raw HTTP responses
     * @param timeout      the timeout duration for API requests
     * @param supportedCapabilities the set of capabilities supported by this model
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
    public Set<Capability> supportedCapabilities() {
        return supportedCapabilities;
    }

    @Override
    public void chat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        ChatRequestValidationUtils.validateMessages(chatRequest.messages());
        ChatRequestParameters parameters = chatRequest.parameters();
        ChatRequestValidationUtils.validateParameters(parameters);
        ResponseFormat responseFormat = parameters.responseFormat();

        // TODO use StreamingChatResponseHandler instead
        StreamingResponseHandler<AiMessage> legacyHandler = new StreamingResponseHandler<>() {

            @Override
            public void onNext(String token) {
                handler.onPartialResponse(token);
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                ChatResponse chatResponse = ChatResponse.builder()
                        .aiMessage(response.content())
                        .metadata(ChatResponseMetadata.builder()
                                .tokenUsage(response.tokenUsage())
                                .finishReason(response.finishReason())
                                .build())
                        .build();
                handler.onCompleteResponse(chatResponse);
            }

            @Override
            public void onError(Throwable error) {
                handler.onError(error);
            }
        };

        List<ToolSpecification> toolSpecifications = parameters.toolSpecifications();
        if (isNullOrEmpty(toolSpecifications)) {
            generate(chatRequest.messages(), legacyHandler, responseFormat);
        } else {
            if (parameters.toolChoice() == REQUIRED) {
                if (toolSpecifications.size() != 1) {
                    throw new UnsupportedFeatureException(String.format(
                            "%s.%s is currently supported only when there is a single tool",
                            ToolChoice.class.getSimpleName(), REQUIRED.name()));
                }
                generate(chatRequest.messages(), toolSpecifications.get(0), legacyHandler, responseFormat);
            } else {
                generate(chatRequest.messages(), toolSpecifications, legacyHandler, responseFormat);
            }
        }
    }

    private void generate(
            List<ChatMessage> messages,
            List<ToolSpecification> toolSpecifications,
            StreamingResponseHandler<AiMessage> handler,
            ResponseFormat responseFormat) {
        generate(messages, toolSpecifications, null, handler, responseFormat);
    }

    private void generate(
            List<ChatMessage> messages,
            ToolSpecification toolSpecification,
            StreamingResponseHandler<AiMessage> handler,
            ResponseFormat responseFormat) {
        generate(messages, null, toolSpecification, handler, responseFormat);
    }

    private void generate(
            List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler, ResponseFormat responseFormat) {
        generate(messages, null, null, handler, responseFormat);
    }

    private void generate(
            List<ChatMessage> messages,
            List<ToolSpecification> toolSpecifications,
            ToolSpecification toolThatMustBeExecuted,
            StreamingResponseHandler<AiMessage> handler,
            ResponseFormat responseFormat) {
        ensureNotEmpty(messages, "messages");

        MistralAiChatCompletionRequest.MistralAiChatCompletionRequestBuilder requestBuilder =
                MistralAiChatCompletionRequest.builder()
                        .model(this.modelName)
                        .messages(toMistralAiMessages(messages))
                        .temperature(this.temperature)
                        .maxTokens(this.maxTokens)
                        .topP(this.topP)
                        .randomSeed(this.randomSeed)
                        .safePrompt(this.safePrompt)
                        .stream(true)
                        .responseFormat(toMistralAiResponseFormat(responseFormat, this.responseFormat));

        if (!isNullOrEmpty(toolSpecifications)) {
            requestBuilder.tools(toMistralAiTools(toolSpecifications));
            requestBuilder.toolChoice(MistralAiToolChoiceName.AUTO);
        } else if (toolThatMustBeExecuted != null) {
            requestBuilder.tools(toMistralAiTools(singletonList(toolThatMustBeExecuted)));
            requestBuilder.toolChoice(
                    MistralAiToolChoiceName
                            .ANY); // MistralAi does not support toolChoice as Function object. ANY force to the model
            // to call a function
        }

        MistralAiChatCompletionRequest request = requestBuilder.build();

        client.streamingChatCompletion(request, handler);
    }

    @Override
    public ModelProvider provider() {
        return MISTRAL_AI;
    }

    public static MistralAiStreamingChatModelBuilder builder() {
        for (MistralAiStreamingChatModelBuilderFactory factory :
                loadFactories(MistralAiStreamingChatModelBuilderFactory.class)) {
            return factory.get();
        }
        return new MistralAiStreamingChatModelBuilder();
    }

    public static class MistralAiStreamingChatModelBuilder {

        private String baseUrl;

        private String apiKey;

        private String modelName;

        private Double temperature;

        private Double topP;

        private Integer maxTokens;

        private Boolean safePrompt;

        private Integer randomSeed;

        private ResponseFormat responseFormat;

        private Boolean logRequests;

        private Boolean logResponses;

        private Duration timeout;

        private Set<Capability> supportedCapabilities;

        private HttpClientBuilder httpClientBuilder;

        public MistralAiStreamingChatModelBuilder() {}

        public MistralAiStreamingChatModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public MistralAiStreamingChatModelBuilder modelName(MistralAiChatModelName modelName) {
            this.modelName = modelName.toString();
            return this;
        }

        /**
         * @deprecated please use {@link #responseFormat(ResponseFormat)} instead
         */
        @Deprecated(forRemoval = true)
        public MistralAiStreamingChatModelBuilder responseFormat(String responseFormat) {
            this.responseFormat = MistralAiResponseFormatType.valueOf(responseFormat.toUpperCase())
                    .toGenericResponseFormat();
            return this;
        }

        /**
         * @deprecated please use {@link #responseFormat(ResponseFormat)} instead
         */
        @Deprecated(forRemoval = true)
        public MistralAiStreamingChatModelBuilder responseFormat(MistralAiResponseFormatType responseFormat) {
            this.responseFormat = responseFormat.toGenericResponseFormat();
            return this;
        }

        public MistralAiStreamingChatModelBuilder responseFormat(ResponseFormat responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        /**
         * @param baseUrl      the base URL of the Mistral AI API. It uses the default value if not specified
         * @return {@code this}.
         */
        public MistralAiStreamingChatModelBuilder baseUrl(final String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * @param apiKey       the API key for authentication
         * @return {@code this}.
         */
        public MistralAiStreamingChatModelBuilder apiKey(final String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * @param temperature  the temperature parameter for generating chat responses
         * @return {@code this}.
         */
        public MistralAiStreamingChatModelBuilder temperature(final Double temperature) {
            this.temperature = temperature;
            return this;
        }

        /**
         * @param topP         the top-p parameter for generating chat responses
         * @return {@code this}.
         */
        public MistralAiStreamingChatModelBuilder topP(final Double topP) {
            this.topP = topP;
            return this;
        }

        /**
         * @param maxTokens    the maximum number of new tokens to generate in a chat response
         * @return {@code this}.
         */
        public MistralAiStreamingChatModelBuilder maxTokens(final Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        /**
         * @param safePrompt   a flag indicating whether to use a safe prompt for generating chat responses
         * @return {@code this}.
         */
        public MistralAiStreamingChatModelBuilder safePrompt(final Boolean safePrompt) {
            this.safePrompt = safePrompt;
            return this;
        }

        /**
         * @param randomSeed   the random seed for generating chat responses
         *                     (if not specified, a random number is used)
         * @return {@code this}.
         */
        public MistralAiStreamingChatModelBuilder randomSeed(final Integer randomSeed) {
            this.randomSeed = randomSeed;
            return this;
        }

        /**
         * @param logRequests  a flag indicating whether to log raw HTTP requests
         * @return {@code this}.
         */
        public MistralAiStreamingChatModelBuilder logRequests(final Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        /**
         * @param logResponses a flag indicating whether to log raw HTTP responses
         * @return {@code this}.
         */
        public MistralAiStreamingChatModelBuilder logResponses(final Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiStreamingChatModelBuilder timeout(final Duration timeout) {
            this.timeout = timeout;
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

        public MistralAiStreamingChatModelBuilder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        public MistralAiStreamingChatModel build() {
            return new MistralAiStreamingChatModel(
                    this.httpClientBuilder,
                    this.baseUrl,
                    this.apiKey,
                    this.modelName,
                    this.temperature,
                    this.topP,
                    this.maxTokens,
                    this.safePrompt,
                    this.randomSeed,
                    this.responseFormat,
                    this.logRequests,
                    this.logResponses,
                    this.timeout,
                    this.supportedCapabilities);
        }

        @Override
        public String toString() {
            return "MistralAiStreamingChatModel.MistralAiStreamingChatModelBuilder("
                                    + "baseUrl=" + this.baseUrl
                                    + ", apiKey=" + this.apiKey
                            == null
                    ? ""
                    : "*****"
                            + ", modelName=" + this.modelName
                            + ", temperature=" + this.temperature
                            + ", topP=" + this.topP
                            + ", maxTokens=" + this.maxTokens
                            + ", safePrompt=" + this.safePrompt
                            + ", randomSeed=" + this.randomSeed
                            + ", responseFormat=" + this.responseFormat
                            + ", logRequests=" + this.logRequests
                            + ", logResponses=" + this.logResponses
                            + ", timeout=" + this.timeout
                            + ", supportedCapabilities=" + this.supportedCapabilities
                            + ")";
        }
    }
}
