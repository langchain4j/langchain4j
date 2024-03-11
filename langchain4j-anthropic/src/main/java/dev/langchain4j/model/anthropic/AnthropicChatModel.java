package dev.langchain4j.model.anthropic;

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
import static dev.langchain4j.model.anthropic.AnthropicHelper.ANTHROPIC_API_URL;
import static dev.langchain4j.model.anthropic.AnthropicHelper.finishReasonFrom;
import static dev.langchain4j.model.anthropic.AnthropicHelper.toAnthropicAiMessages;
import static dev.langchain4j.model.anthropic.AnthropicHelper.tokenUsageFrom;
import static java.util.stream.Collectors.joining;

/**
 * Represents an Anthropic Chat Model with a chat completion interface.
 * You can find description of parameters <a href="https://docs.anthropic.com/claude/reference/messages_post">here</a>.
 */
public class AnthropicChatModel implements ChatLanguageModel {

    private final AnthropicClient client;
    private final String modelName;
    private final Integer maxTokens;
    private final Integer maxRetries;

    /**
     * Constructs an AnthropicChatModel with the specified parameters.
     *
     * @param baseUrl    the base URL of the Anthropic API. If not specified, the default ANTHROPIC_API_URL is used.
     * @param apiKey     the API key for authentication with the Anthropic service.
     * @param timeout    the timeout duration for API requests. If not specified, the default value is 60 seconds.
     * @param modelName  the name of the Anthropic model to use for generating responses.
     * @param maxTokens  the maximum number of new tokens to generate in a chat response.
     * @param maxRetries the maximum number of retries for API requests. If not specified, the default value is 3.
     */
    @Builder
    public AnthropicChatModel(String baseUrl,
                              String apiKey,
                              Duration timeout,
                              String modelName,
                              Integer maxTokens,
                              Integer maxRetries) {
        this.client = AnthropicClient.builder()
                .baseUrl(getOrDefault(baseUrl, ANTHROPIC_API_URL))
                .apiKey(apiKey)
                .timeout(getOrDefault(timeout, Duration.ofSeconds(60)))
                .build();
        this.modelName = modelName;
        this.maxTokens = maxTokens;
        this.maxRetries = getOrDefault(maxRetries, 3);
    }

    /**
     * Creates a AnthropicChatModel with the specified API key.
     *
     * @param apiKey the API key for authentication
     * @return a AnthropicChatModel instance
     */
    public static AnthropicChatModel withApiKey(String apiKey) {
        return builder().apiKey(apiKey).build();
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        ensureNotEmpty(messages, "messages");

        AnthropicChatRequest anthropicChatRequest = AnthropicChatRequest.builder()
                .model(modelName)
                .maxTokens(maxTokens)
                .messages(toAnthropicAiMessages(messages))
                .stream(false)
                .build();

        AnthropicChatResponse response = withRetry(() -> client.chatCompletion(anthropicChatRequest), maxRetries);

        return Response.from(
                aiMessage(response.getContent().stream().map(AnthropicChatResponse.Content::getText).collect(joining(","))),
                tokenUsageFrom(response.getUsage()),
                finishReasonFrom(response.getStopReason())
        );
    }

}
