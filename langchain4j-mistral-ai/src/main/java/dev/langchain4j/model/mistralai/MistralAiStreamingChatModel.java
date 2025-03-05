package dev.langchain4j.model.mistralai;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.model.chat.request.ToolChoice.REQUIRED;
import static dev.langchain4j.model.mistralai.internal.mapper.MistralAiMapper.*;
import static dev.langchain4j.model.mistralai.internal.mapper.MistralAiMapper.toMistralAiTools;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ChatRequestValidator;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.mistralai.internal.api.MistralAiChatCompletionRequest;
import dev.langchain4j.model.mistralai.internal.api.MistralAiResponseFormatType;
import dev.langchain4j.model.mistralai.internal.api.MistralAiToolChoiceName;
import dev.langchain4j.model.mistralai.internal.client.MistralAiClient;
import dev.langchain4j.model.mistralai.spi.MistralAiStreamingChatModelBuilderFactory;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a Mistral AI Chat Model with a chat completion interface, such as mistral-tiny and mistral-small.
 * The model's response is streamed token by token and should be handled with {@link StreamingResponseHandler}.
 * You can find description of parameters <a href="https://docs.mistral.ai/api/#operation/createChatCompletion">here</a>.
 */
public class MistralAiStreamingChatModel implements StreamingChatLanguageModel {

    private final MistralAiClient client;
    private final String modelName;
    private final Double temperature;
    private final Double topP;
    private final Integer maxTokens;
    private final Boolean safePrompt;
    private final Integer randomSeed;
    private final String responseFormat;
    private final List<ChatModelListener> listeners;

    /**
     * Constructs a MistralAiStreamingChatModel with the specified parameters.
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
     */
    public MistralAiStreamingChatModel(
            String baseUrl,
            String apiKey,
            String modelName,
            Double temperature,
            Double topP,
            Integer maxTokens,
            Boolean safePrompt,
            Integer randomSeed,
            String responseFormat,
            Boolean logRequests,
            Boolean logResponses,
            Duration timeout,
            List<ChatModelListener> listeners) {

        this.client = MistralAiClient.builder()
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
        this.listeners = listeners == null ? emptyList() : new ArrayList<>(listeners);
    }

    @Override
    public List<ChatModelListener> listeners() {
        return this.listeners;
    }

    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        ChatRequestValidator.validateMessages(chatRequest.messages());
        ChatRequestParameters parameters = chatRequest.parameters();
        ChatRequestValidator.validateParameters(parameters);
        ChatRequestValidator.validate(parameters.responseFormat());
        MistralAiChatCompletionRequest.MistralAiChatCompletionRequestBuilder requestBuilder =
                MistralAiChatCompletionRequest.builder()
                        .model(parameters.modelName())
                        .messages(toMistralAiMessages(chatRequest.messages()))
                        .temperature(parameters.temperature())
                        .maxTokens(parameters.maxOutputTokens())
                        .topP(parameters.topP())
                        .responseFormat(toMistralAiResponseFormat(parameters.responseFormat()));
        if (parameters instanceof MistralAiChatRequestParameters) {
            MistralAiChatRequestParameters mistralParameters = (MistralAiChatRequestParameters) parameters;
            requestBuilder.randomSeed(mistralParameters.randomSeed()).safePrompt(mistralParameters.safePrompt()).stream(
                    mistralParameters.stream());
        }
        List<ToolSpecification> toolSpecifications = parameters.toolSpecifications();
        if (!isNullOrEmpty(toolSpecifications)) {
            if (parameters.toolChoice() == REQUIRED) {
                if (toolSpecifications.size() != 1) {
                    throw new UnsupportedFeatureException(String.format(
                            "%s.%s is currently supported only when there is a single tool",
                            ToolChoice.class.getSimpleName(), REQUIRED.name()));
                }
                requestBuilder.tools(toMistralAiTools(parameters.toolSpecifications()));
                // MistralAi does not support toolChoice as Function object. ANY force to the model to call a function
                requestBuilder.toolChoice(MistralAiToolChoiceName.ANY);
            } else {
                requestBuilder.tools(toMistralAiTools(parameters.toolSpecifications()));
                requestBuilder.toolChoice(MistralAiToolChoiceName.AUTO);
            }
        }
        MistralAiChatCompletionRequest request = requestBuilder.build();
        client.streamingChatCompletion(request, handler);
    }

    private void generate(
            List<ChatMessage> messages,
            List<ToolSpecification> toolSpecifications,
            StreamingResponseHandler<AiMessage> handler) {
        generate(messages, toolSpecifications, null, handler);
    }

    private void generate(
            List<ChatMessage> messages,
            ToolSpecification toolSpecification,
            StreamingResponseHandler<AiMessage> handler) {
        generate(messages, null, toolSpecification, handler);
    }

    private void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
        generate(messages, null, null, handler);
    }

    private void generate(
            List<ChatMessage> messages,
            List<ToolSpecification> toolSpecifications,
            ToolSpecification toolThatMustBeExecuted,
            StreamingResponseHandler<AiMessage> handler) {
        ensureNotEmpty(messages, "messages");

        MistralAiChatRequestParameters.Builder parametersBuilder = MistralAiChatRequestParameters.builder()
                .modelName(modelName)
                .maxOutputTokens(maxTokens)
                .temperature(temperature)
                .topP(topP)
                .randomSeed(this.randomSeed)
                .responseFormat(toResponseFormat(responseFormat))
                .safePrompt(this.safePrompt)
                .stream(true);
        if (!isNullOrEmpty(toolSpecifications)) {
            parametersBuilder.toolSpecifications(toolSpecifications);
            parametersBuilder.toolChoice(ToolChoice.AUTO);
        } else if (toolThatMustBeExecuted != null) {
            parametersBuilder.toolSpecifications(singletonList(toolThatMustBeExecuted));
            parametersBuilder.toolChoice(ToolChoice.REQUIRED);
            // MistralAi does not support toolChoice as Function object. ANY force to the model to call a function
        }
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(messages)
                .parameters(parametersBuilder.build())
                .build();

        chat(chatRequest, convertHandler(handler));
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

        private String responseFormat;

        private Boolean logRequests;

        private Boolean logResponses;

        private Duration timeout;

        private List<ChatModelListener> listeners;

        public MistralAiStreamingChatModelBuilder() {}

        public MistralAiStreamingChatModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public MistralAiStreamingChatModelBuilder modelName(MistralAiChatModelName modelName) {
            this.modelName = modelName.toString();
            return this;
        }

        public MistralAiStreamingChatModelBuilder responseFormat(String responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        public MistralAiStreamingChatModelBuilder responseFormat(MistralAiResponseFormatType responseFormat) {
            this.responseFormat = responseFormat.toString();
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

        /**
         * @param listeners    the list of ChatModelListener listening on the StreamingChatModelL usage.
         * @return {@code this}.
         */
        public MistralAiStreamingChatModelBuilder listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public MistralAiStreamingChatModel build() {
            return new MistralAiStreamingChatModel(
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
                    this.listeners);
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
                            + ", listeners=" + this.listeners
                            + ")";
        }
    }
}
