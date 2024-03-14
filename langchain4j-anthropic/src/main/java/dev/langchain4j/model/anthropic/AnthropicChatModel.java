package dev.langchain4j.model.anthropic;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.Builder;

import java.time.Duration;
import java.util.List;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.model.anthropic.AnthropicMapper.*;

/**
 * Represents an Anthropic language model with a Messages API.
 * <br>
 * More details are available <a href="https://docs.anthropic.com/claude/reference/messages_post">here</a>.
 * <br>
 * <br>
 * It supports {@link Image}s as inputs. {@link UserMessage}s can contain one or multiple {@link ImageContent}s.
 * {@link Image}s must not be represented as URLs; they should be Base64-encoded strings and include a {@code mimeType}.
 * <br>
 * <br>
 * The content of {@link SystemMessage}s is sent using the "system" parameter.
 * If there are multiple {@link SystemMessage}s, they are concatenated with a double newline (\n\n).
 * <br>
 * <br>
 * Does not support tools.
 */
public class AnthropicChatModel implements ChatLanguageModel {

    private final AnthropicClient client;
    private final String modelName;
    private final Double temperature;
    private final Double topP;
    private final Integer topK;
    private final int maxTokens;
    private final List<String> stopSequences;
    private final int maxRetries;

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
     * @param timeout       The timeout for API requests. Default: 60 seconds
     * @param maxRetries    The maximum number of retries for API requests. Default: 3
     * @param logRequests   Whether to log the content of API requests using SLF4J. Default: false
     * @param logResponses  Whether to log the content of API responses using SLF4J. Default: false
     */
    @Builder
    private AnthropicChatModel(String baseUrl,
                               String apiKey,
                               String version,
                               String modelName,
                               Double temperature,
                               Double topP,
                               Integer topK,
                               Integer maxTokens,
                               List<String> stopSequences,
                               Duration timeout,
                               Integer maxRetries,
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
        this.maxRetries = getOrDefault(maxRetries, 3);
    }

    public static class AnthropicChatModelBuilder {

        public AnthropicChatModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public AnthropicChatModelBuilder modelName(AnthropicChatModelName modelName) {
            this.modelName = modelName.toString();
            return this;
        }
    }

    /**
     * Creates an instance of {@code AnthropicChatModel} with the specified API key.
     *
     * @param apiKey the API key for authentication
     * @return an {@code AnthropicChatModel} instance
     */
    public static AnthropicChatModel withApiKey(String apiKey) {
        return builder().apiKey(apiKey).build();
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {

        AnthropicCreateMessageRequest request = AnthropicCreateMessageRequest.builder()
                .model(modelName)
                .messages(toAnthropicMessages(ensureNotEmpty(messages, "messages")))
                .system(toAnthropicSystemPrompt(messages))
                .maxTokens(maxTokens)
                .stopSequences(stopSequences)
                .stream(false)
                .temperature(temperature)
                .topP(topP)
                .topK(topK)
                .build();

        AnthropicCreateMessageResponse response = withRetry(() -> client.createMessage(request), maxRetries);

        return Response.from(
                toAiMessage(response.getContent()),
                toTokenUsage(response.getUsage()),
                toFinishReason(response.getStopReason())
        );
    }
}
