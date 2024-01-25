package dev.langchain4j.model.mistralai;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import lombok.Builder;

import java.time.Duration;
import java.util.List;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.model.mistralai.DefaultMistralAiHelper.MISTRALAI_API_URL;
import static dev.langchain4j.model.mistralai.DefaultMistralAiHelper.toMistralAiMessages;

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
     * @param logRequests  a flag indicating whether to log raw HTTP requests
     * @param logResponses a flag indicating whether to log raw HTTP responses
     * @param timeout      the timeout duration for API requests
     */
    @Builder
    public MistralAiStreamingChatModel(String baseUrl,
                                       String apiKey,
                                       String modelName,
                                       Double temperature,
                                       Double topP,
                                       Integer maxTokens,
                                       Boolean safePrompt,
                                       Integer randomSeed,
                                       Boolean logRequests,
                                       Boolean logResponses,
                                       Duration timeout) {

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
    }

    /**
     * Creates a MistralAiStreamingChatModel with the specified API key.
     *
     * @param apiKey the API key for authentication
     * @return a MistralAiStreamingChatModel instance
     */
    public static MistralAiStreamingChatModel withApiKey(String apiKey) {
        return builder().apiKey(apiKey).build();
    }

    /**
     * Generates streamed token response based on the given list of messages.
     *
     * @param messages the list of chat messages
     * @param handler  the response handler for processing the generated chat chunk responses
     */
    @Override
    public void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
        ensureNotEmpty(messages, "messages");

        MistralAiChatCompletionRequest request = MistralAiChatCompletionRequest.builder()
                .model(this.modelName)
                .messages(toMistralAiMessages(messages))
                .temperature(this.temperature)
                .maxTokens(this.maxTokens)
                .topP(this.topP)
                .randomSeed(this.randomSeed)
                .safePrompt(this.safePrompt)
                .stream(true)
                .build();

        client.streamingChatCompletion(request, handler);
    }
}
