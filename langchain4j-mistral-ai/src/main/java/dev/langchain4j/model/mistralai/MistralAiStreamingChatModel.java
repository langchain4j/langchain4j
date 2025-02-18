package dev.langchain4j.model.mistralai;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.model.mistralai.internal.mapper.MistralAiMapper.*;
import static dev.langchain4j.model.mistralai.internal.mapper.MistralAiMapper.toMistralAiTools;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.util.Collections.singletonList;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.mistralai.internal.api.MistralAiChatCompletionRequest;
import dev.langchain4j.model.mistralai.internal.api.MistralAiResponseFormatType;
import dev.langchain4j.model.mistralai.internal.api.MistralAiToolChoiceName;
import dev.langchain4j.model.mistralai.internal.client.MistralAiClient;
import dev.langchain4j.model.mistralai.spi.MistralAiStreamingChatModelBuilderFactory;
import java.time.Duration;
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
            Duration timeout) {

        this.client = MistralAiClient.builder()
                .baseUrl(getOrDefault(baseUrl, "https://api.mistral.ai/v1"))
                .apiKey(apiKey)
                .timeout(getOrDefault(timeout, Duration.ofSeconds(60)))
                .logRequests(getOrDefault(logRequests, false))
                .logResponses(getOrDefault(logResponses, false))
                .build();
        this.modelName = getOrDefault(modelName, MistralAiChatModelName.OPEN_MISTRAL_7B.toString());
        this.temperature = temperature;
        this.topP = topP;
        this.maxTokens = maxTokens;
        this.safePrompt = safePrompt;
        this.randomSeed = randomSeed;
        this.responseFormat = responseFormat;
    }

    /**
     * @deprecated Please use {@code builder()} instead, and explicitly set the model name and,
     * if necessary, other parameters.
     * <b>The default value for the model name will be removed in future releases!</b>
     */
    @Deprecated(forRemoval = true)
    public static MistralAiStreamingChatModel withApiKey(String apiKey) {
        return builder().apiKey(apiKey).build();
    }

    /**
     * Generates streamed token response based on the given list of messages and tool specifications.
     *
     * @param messages the list of chat messages
     * @param toolSpecifications the list of tool specifications. tool_choice is set to AUTO.
     * @param handler  the response handler for processing the generated chat chunk responses
     */
    @Override
    public void generate(
            List<ChatMessage> messages,
            List<ToolSpecification> toolSpecifications,
            StreamingResponseHandler<AiMessage> handler) {
        generate(messages, toolSpecifications, null, handler);
    }

    /**
     * Generates streamed token response based on the given list of messages and tool specification.
     *
     * @param messages the list of chat messages
     * @param toolSpecification the tool specification. tool_choice is set to ANY.
     * @param handler  the response handler for processing the generated chat chunk responses
     */
    @Override
    public void generate(
            List<ChatMessage> messages,
            ToolSpecification toolSpecification,
            StreamingResponseHandler<AiMessage> handler) {
        generate(messages, null, toolSpecification, handler);
    }

    /**
     * Generates streamed token response based on the given list of messages.
     *
     * @param messages the list of chat messages
     * @param handler  the response handler for processing the generated chat chunk responses
     */
    @Override
    public void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
        generate(messages, null, null, handler);
    }

    private void generate(
            List<ChatMessage> messages,
            List<ToolSpecification> toolSpecifications,
            ToolSpecification toolThatMustBeExecuted,
            StreamingResponseHandler<AiMessage> handler) {
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
                        .responseFormat(toMistralAiResponseFormat(this.responseFormat));

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
                    this.timeout);
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
                            + ")";
        }
    }
}
