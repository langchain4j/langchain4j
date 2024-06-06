package dev.langchain4j.model.mistralai;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.mistralai.internal.api.MistralAiChatCompletionRequest;
import dev.langchain4j.model.mistralai.internal.api.MistralAiChatCompletionResponse;
import dev.langchain4j.model.mistralai.internal.api.MistralAiResponseFormatType;
import dev.langchain4j.model.mistralai.internal.api.MistralAiToolChoiceName;
import dev.langchain4j.model.mistralai.internal.client.MistralAiClient;
import dev.langchain4j.model.mistralai.spi.MistralAiChatModelBuilderFactory;
import dev.langchain4j.model.output.Response;
import lombok.Builder;

import java.time.Duration;
import java.util.List;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.model.mistralai.internal.mapper.MistralAiMapper.*;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.util.Collections.singletonList;

/**
 * Represents a Mistral AI Chat Model with a chat completion interface, such as open-mistral-7b and open-mixtral-8x7b
 * This model allows generating chat completion of a sync way based on a list of chat messages.
 * You can find description of parameters <a href="https://docs.mistral.ai/api/#operation/createChatCompletion">here</a>.
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

    /**
     * Constructs a MistralAiChatModel with the specified parameters.
     *
     * @param baseUrl      the base URL of the Mistral AI API. It uses the default value if not specified
     * @param apiKey       the API key for authentication
     * @param modelName    the name of the Mistral AI model to use
     * @param temperature  the temperature parameter for generating chat responses
     * @param topP         the top-p parameter for generating chat responses
     * @param maxTokens    the maximum number of new tokens to generate in a chat response
     * @param safePrompt   a flag indicating whether to use a safe prompt for generating chat responses
     * @param randomSeed   the random seed for generating chat responses
     * @param responseFormat the response format for generating chat responses.
     *                       <p>
     *                       Current values supported are "text" and "json_object".
     * @param timeout      the timeout duration for API requests
     *                     <p>
     *                     The default value is 60 seconds
     * @param logRequests  a flag indicating whether to log API requests
     * @param logResponses a flag indicating whether to log API responses
     * @param maxRetries   the maximum number of retries for API requests. It uses the default value 3 if not specified
     */
    @Builder
    public MistralAiChatModel(String baseUrl,
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
                              Integer maxRetries) {

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
        this.maxRetries = getOrDefault(maxRetries, 3);
    }

    /**
     * Creates a MistralAiChatModel with the specified API key.
     *
     * @param apiKey the API key for authentication
     * @return a MistralAiChatModel instance
     */
    public static MistralAiChatModel withApiKey(String apiKey) {
        return builder().apiKey(apiKey).build();
    }

    /**
     * Generates chat response based on the given list of messages.
     *
     * @param messages the list of chat messages
     */
    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        return generate(messages, null, null);
    }

    /**
     * Generates an AI message response based on the given list of chat messages and tool specifications.
     *
     * @param messages the list of chat messages
     * @param toolSpecifications the list of tool specifications. tool_choice is set to AUTO.
     * @return a Response containing the generated AI message
     */
    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        return generate(messages, toolSpecifications, null);
    }

    /**
     * Generates an AI message response based on the given list of chat messages and a tool specification.
     *
     * @param messages the list of chat messages
     * @param toolSpecification the tool specification that must be executed. tool_choice is set to ANY.
     * @return a Response containing the generated AI message
     */
    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, ToolSpecification toolSpecification) {
        return generate(messages, singletonList(toolSpecification), toolSpecification);
    }

    private Response<AiMessage> generate(List<ChatMessage> messages,
                                         List<ToolSpecification> toolSpecifications,
                                         ToolSpecification toolThatMustBeExecuted) {
        ensureNotEmpty(messages, "messages");

        MistralAiChatCompletionRequest.MistralAiChatCompletionRequestBuilder requestBuilder = MistralAiChatCompletionRequest.builder()
                .model(this.modelName)
                .messages(toMistralAiMessages(messages))
                .temperature(this.temperature)
                .maxTokens(this.maxTokens)
                .topP(this.topP)
                .randomSeed(this.randomSeed)
                .safePrompt(this.safePrompt)
                .responseFormat(toMistralAiResponseFormat(this.responseFormat))
                .stream(false);

        if (!isNullOrEmpty(toolSpecifications)) {
            requestBuilder.tools(toMistralAiTools(toolSpecifications));
            requestBuilder.toolChoice(MistralAiToolChoiceName.AUTO);
        } else if (toolThatMustBeExecuted != null) {
            requestBuilder.tools(toMistralAiTools(singletonList(toolThatMustBeExecuted)));
            requestBuilder.toolChoice(MistralAiToolChoiceName.ANY); // MistralAi does not support toolChoice as Function object. ANY force to the model to call a function
        }

        MistralAiChatCompletionRequest request = requestBuilder.build();

        MistralAiChatCompletionResponse response = withRetry(() -> client.chatCompletion(request), maxRetries);

        return Response.from(
                aiMessageFrom(response),
                tokenUsageFrom(response.getUsage()),
                finishReasonFrom(response.getChoices().get(0).getFinishReason())
        );
    }

    public static MistralAiChatModelBuilder builder() {
        for (MistralAiChatModelBuilderFactory factory : loadFactories(MistralAiChatModelBuilderFactory.class)) {
            return factory.get();
        }
        return new MistralAiChatModelBuilder();
    }

    public static class MistralAiChatModelBuilder {

        public MistralAiChatModelBuilder() {
        }

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
    }
}
