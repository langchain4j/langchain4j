package dev.langchain4j.model.anthropic;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import lombok.Builder;

import java.time.Duration;
import java.util.List;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.model.anthropic.AnthropicMapper.toAnthropicMessages;
import static dev.langchain4j.model.anthropic.AnthropicMapper.toAnthropicSystemPrompt;

public class AnthropicStreamingChatModel implements StreamingChatLanguageModel {

    private final AnthropicClient client;
    private final String modelName;
    private final Double temperature;
    private final Double topP;
    private final Integer topK;
    private final int maxTokens;
    private final List<String> stopSequences;

    /**
     * Constructs an instance of an {@code AnthropicChatModel} with the specified parameters.
     *
     * @param baseUrl       The base URL of the Anthropic API. Default: "https://api.anthropic.com/v1/"
     * @param apiKey        The API key for authentication with the Anthropic API.
     * @param version       The version of the Anthropic API. Default: "2023-06-01"
     * @param modelName     The name of the Anthropic model to use. Default: "claude-3-sonnet-20240229"
     * @param temperature   The temperature
     * @param topP          The top-P
     * @param topK          The top-K
     * @param maxTokens     The maximum number of tokens to generate. Default: 1024
     * @param stopSequences The custom text sequences that will cause the model to stop generating
     * @param timeout       The timeout for API requests. Default: 60 seconds*
     * @param logRequests   Whether to log the content of API requests using SLF4J. Default: false
     * @param logResponses  Whether to log the content of API responses using SLF4J. Default: false
     */
    @Builder
    private AnthropicStreamingChatModel(String baseUrl,
                                        String apiKey,
                                        String version,
                                        String modelName,
                                        Double temperature,
                                        Double topP,
                                        Integer topK,
                                        Integer maxTokens,
                                        List<String> stopSequences,
                                        Duration timeout,
                                        Boolean logRequests,
                                        Boolean logResponses) {
        this.client = AnthropicClient.builder()
                .baseUrl(getOrDefault(baseUrl, "https://api.anthropic.com/v1/"))
                .apiKey(apiKey)
                .version(getOrDefault(version, "2023-06-01"))
                .timeout(getOrDefault(timeout, Duration.ofSeconds(60)))
                .logRequests(getOrDefault(logRequests, false))
                .logResponses(getOrDefault(logResponses, false))
                .build();
        this.modelName = getOrDefault(modelName, "claude-3-sonnet-20240229");
        this.temperature = temperature;
        this.topP = topP;
        this.topK = topK;
        this.maxTokens = getOrDefault(maxTokens, 1024);
        this.stopSequences = stopSequences;
    }

    @Override
    public void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
        AnthropicCreateMessageRequest request = AnthropicCreateMessageRequest.builder()
                .model(modelName)
                .messages(toAnthropicMessages(ensureNotEmpty(messages, "messages")))
                .system(toAnthropicSystemPrompt(messages))
                .maxTokens(maxTokens)
                .stopSequences(stopSequences)
                .stream(true)
                .temperature(temperature)
                .topP(topP)
                .topK(topK)
                .build();

        client.streamingMessages(request, handler);
    }
}
