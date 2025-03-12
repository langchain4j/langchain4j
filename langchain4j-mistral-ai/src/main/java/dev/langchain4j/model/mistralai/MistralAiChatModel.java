package dev.langchain4j.model.mistralai;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.model.chat.request.ToolChoice.REQUIRED;
import static dev.langchain4j.model.mistralai.internal.mapper.MistralAiMapper.*;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ChatRequestValidator;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.mistralai.internal.api.MistralAiChatCompletionRequest;
import dev.langchain4j.model.mistralai.internal.api.MistralAiChatCompletionResponse;
import dev.langchain4j.model.mistralai.internal.api.MistralAiResponseFormatType;
import dev.langchain4j.model.mistralai.internal.api.MistralAiToolChoiceName;
import dev.langchain4j.model.mistralai.internal.client.MistralAiClient;
import dev.langchain4j.model.mistralai.spi.MistralAiChatModelBuilderFactory;
import dev.langchain4j.model.output.Response;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a Mistral AI Chat Model with a chat completion interface, such as open-mistral-7b and open-mixtral-8x7b
 * This model allows generating chat completion of a sync way based on a list of chat messages.
 * You can find description of parameters
 * <a href="https://docs.mistral.ai/api/#operation/createChatCompletion">here</a>.
 */
public class MistralAiChatModel implements ChatLanguageModel {

    private final MistralAiClient client;
    private final String modelName;
    private final Double temperature;
    private final Double topP;
    private final Integer maxTokens;
    private final Boolean safePrompt;
    private final Integer randomSeed;
    private final String responseFormat;
    private final Integer maxRetries;
    private final List<ChatModelListener> listeners;

    /**
     * Constructs a MistralAiChatModel with the specified parameters.
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
     */
    public MistralAiChatModel(
            String baseUrl,
            String apiKey,
            String modelName,
            Double temperature,
            Double topP,
            Integer maxTokens,
            Boolean safePrompt,
            Integer randomSeed,
            String responseFormat,
            Duration timeout,
            Boolean logRequests,
            Boolean logResponses,
            Integer maxRetries,
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
        this.maxRetries = getOrDefault(maxRetries, 3);
        this.listeners = listeners == null ? emptyList() : new ArrayList<>(listeners);
    }

    @Override
    public List<ChatModelListener> listeners() {
        return this.listeners;
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
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

        MistralAiChatCompletionResponse response = withRetry(() -> client.chatCompletion(request), maxRetries);
        MistralAiChatResponseMetadata responseMetadata = MistralAiChatResponseMetadata.builder()
                .choices(response.getChoices())
                .created(response.getCreated())
                .object(response.getObject())
                .id(response.getId())
                .modelName(response.getModel())
                .tokenUsage(tokenUsageFrom(response.getUsage()))
                .finishReason(finishReasonFrom(response.getChoices().get(0).getFinishReason()))
                .build();
        return ChatResponse.builder()
                .aiMessage(aiMessageFrom(response))
                .metadata(responseMetadata)
                .build();
    }

    private Response<AiMessage> generate(List<ChatMessage> messages) {
        return generate(messages, null, null);
    }

    private Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        return generate(messages, toolSpecifications, null);
    }

    private Response<AiMessage> generate(List<ChatMessage> messages, ToolSpecification toolSpecification) {
        return generate(messages, singletonList(toolSpecification), toolSpecification);
    }

    private Response<AiMessage> generate(
            List<ChatMessage> messages,
            List<ToolSpecification> toolSpecifications,
            ToolSpecification toolThatMustBeExecuted) {
        ensureNotEmpty(messages, "messages");

        MistralAiChatRequestParameters.Builder parametersBuilder = MistralAiChatRequestParameters.builder()
                .modelName(modelName)
                .maxOutputTokens(maxTokens)
                .temperature(temperature)
                .topP(topP)
                .randomSeed(this.randomSeed)
                .responseFormat(toResponseFormat(responseFormat))
                .safePrompt(this.safePrompt)
                .stream(false);
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

        return convertResponse(chat(chatRequest));
    }

    public static MistralAiChatModelBuilder builder() {
        for (MistralAiChatModelBuilderFactory factory : loadFactories(MistralAiChatModelBuilderFactory.class)) {
            return factory.get();
        }
        return new MistralAiChatModelBuilder();
    }

    public static class MistralAiChatModelBuilder {

        private String baseUrl;

        private String apiKey;

        private String modelName;

        private Double temperature;

        private Double topP;

        private Integer maxTokens;

        private Boolean safePrompt;

        private Integer randomSeed;

        private String responseFormat;

        private Duration timeout;

        private Boolean logRequests;

        private Boolean logResponses;

        private Integer maxRetries;

        private List<ChatModelListener> listeners;

        public MistralAiChatModelBuilder() {}

        public MistralAiChatModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public MistralAiChatModelBuilder modelName(MistralAiChatModelName modelName) {
            this.modelName = modelName.toString();
            return this;
        }

        public MistralAiChatModelBuilder responseFormat(String responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        public MistralAiChatModelBuilder responseFormat(MistralAiResponseFormatType responseFormat) {
            this.responseFormat = responseFormat.toString();
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

        /**
         * @param listeners the list of ChatModelListener listening on the
         * StreamingChatModelL usage.
         * @return {@code this}.
         */
        public MistralAiChatModelBuilder listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public MistralAiChatModel build() {
            return new MistralAiChatModel(
                    this.baseUrl,
                    this.apiKey,
                    this.modelName,
                    this.temperature,
                    this.topP,
                    this.maxTokens,
                    this.safePrompt,
                    this.randomSeed,
                    this.responseFormat,
                    this.timeout,
                    this.logRequests,
                    this.logResponses,
                    this.maxRetries,
                    this.listeners);
        }

        @Override
        public String toString() {
            return "MistralAiChatModelBuilder(" + "baseUrl=" + this.baseUrl + ", apiKey=" + this.apiKey == null
                    ? ""
                    : "*****"
                            + ", modelName=" + this.modelName
                            + ", temperature=" + this.temperature
                            + ", topP=" + this.topP
                            + ", maxTokens=" + this.maxTokens
                            + ", safePrompt=" + this.safePrompt
                            + ", randomSeed=" + this.randomSeed
                            + ", responseFormat=" + this.responseFormat
                            + ", timeout=" + this.timeout
                            + ", logRequests=" + this.logRequests
                            + ", logResponses=" + this.logResponses
                            + ", maxRetries=" + this.maxRetries
                            + ", listeners=" + this.listeners
                            + ")";
        }
    }
}
