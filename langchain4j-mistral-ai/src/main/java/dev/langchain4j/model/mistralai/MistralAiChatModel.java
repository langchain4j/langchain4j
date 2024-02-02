package dev.langchain4j.model.mistralai;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.Builder;

import java.time.Duration;
import java.util.List;

import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.model.mistralai.DefaultMistralAiHelper.*;

/**
 * Represents a Mistral AI Chat Model with a chat completion interface, such as mistral-tiny and mistral-small.
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
                              Duration timeout,
                              Boolean logRequests,
                              Boolean logResponses,
                              Integer maxRetries) {

        this.client = MistralAiClient.builder()
                .baseUrl(getOrDefault(baseUrl, MISTRALAI_API_URL))
                .apiKey(apiKey)
                .timeout(getOrDefault(timeout, Duration.ofSeconds(60)))
                .logRequests(getOrDefault(logRequests, false))
                .logResponses(getOrDefault(logResponses, false))
                .build();
        this.modelName = getOrDefault(modelName, MistralAiChatModelName.MISTRAL_TINY.toString());
        this.temperature = temperature;
        this.topP = topP;
        this.maxTokens = maxTokens;
        this.safePrompt = safePrompt;
        this.randomSeed = randomSeed;
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
        ensureNotEmpty(messages, "messages");

        MistralAiChatCompletionRequest request = MistralAiChatCompletionRequest.builder()
                .model(this.modelName)
                .messages(toMistralAiMessages(messages))
                .temperature(this.temperature)
                .maxTokens(this.maxTokens)
                .topP(this.topP)
                .randomSeed(this.randomSeed)
                .safePrompt(this.safePrompt)
                .stream(false)
                .build();

        MistralAiChatCompletionResponse response = withRetry(() -> client.chatCompletion(request), maxRetries);
        return Response.from(
                aiMessage(response.getChoices().get(0).getMessage().getContent()),
                tokenUsageFrom(response.getUsage()),
                finishReasonFrom(response.getChoices().get(0).getFinishReason())
        );
    }
}
