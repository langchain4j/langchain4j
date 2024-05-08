package dev.langchain4j.model.cohere;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.cohere.internal.api.CohereChatRequest;
import dev.langchain4j.model.cohere.internal.api.CohereChatResponse;
import dev.langchain4j.model.cohere.internal.client.CohereClient;
import dev.langchain4j.model.output.Response;
import lombok.Builder;

import java.time.Duration;
import java.util.List;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.model.cohere.internal.mapper.CohereMapper.*;

/**
 * An implementation of a ChatModel that uses
 * <a href="https://docs.cohere.com/docs/command-r">Cohere Command R API</a>.
 */
public class CohereChatModel implements ChatLanguageModel {

    private static final String DEFAULT_BASE_URL = "https://api.cohere.ai/v1/";

    private final CohereClient client;
    private final String modelName;
    private final Double temperature;
    private final Double topP;
    private final Integer topK;
    private final Integer maxTokens;
    private final List<String> stopSequences;
    private final Integer maxRetries;

    /**
     * Constructs an instance of an {@code CohereChatModel} with the specified parameters.
     *
     * @param baseUrl       The base URL of the Cohere API. Default: "https://api.cohere.ai/v1/"
     * @param apiKey        The API key for authentication with the Cohere API.
     * @param modelName     The name of the Cohere model to use. Default: "claude-3-haiku-20240307"
     * @param temperature   The temperature. Default: 0.3
     * @param topP          The top-P. Defaults to 0.75. min value of 0.01, max value of 0.99.
     * @param topK          The top-K. Defaults to 0, min value of 0, max value of 500.
     * @param maxTokens     The maximum number of tokens the model will generate as part of the response.
     * @param stopSequences The custom text sequences that will cause the model to stop generating
     * @param timeout       The timeout for API requests. Default: 60 seconds
     * @param maxRetries    The maximum number of retries for API requests. Default: 3
     * @param logRequests   Whether to log the content of API requests using SLF4J. Default: false
     * @param logResponses  Whether to log the content of API responses using SLF4J. Default: false
     */
    @Builder
    private CohereChatModel(String baseUrl,
                            String apiKey,
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
        this.client = CohereClient.builder()
                .baseUrl(getOrDefault(baseUrl, DEFAULT_BASE_URL))
                .apiKey(apiKey)
                .timeout(getOrDefault(timeout, Duration.ofSeconds(60)))
                .logRequests(getOrDefault(logRequests, false))
                .logResponses(getOrDefault(logResponses, false))
                .build();
        this.modelName = getOrDefault(modelName, "command-r");
        this.temperature = temperature;
        this.topP = topP;
        this.topK = topK;
        this.maxTokens = getOrDefault(maxTokens, 1024);
        this.stopSequences = stopSequences;
        this.maxRetries = getOrDefault(maxRetries, 3);
    }

    /**
     * Creates an instance of {@code CohereChatModel} with the specified API key.
     *
     * @param apiKey the API key for authentication
     * @return an {@code CohereChatModel} instance
     */
    public static CohereChatModel withApiKey(String apiKey) {
        return builder().apiKey(apiKey).build();
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        return generate(messages, (List<ToolSpecification>) null);
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> tools) {
        ensureNotEmpty(messages, "messages");

        CohereChatRequest request = CohereChatRequest.builder()
                .model(modelName)
                .preamble(toPreamble(messages))
                .message(toCohereMessage(messages.get(messages.size() - 1)))
                .toolResults(toToolResults(messages))
                .chatHistory(toChatHistory(messages.subList(0, messages.size() - 1)))
                .maxTokens(maxTokens)
                .stopSequences(stopSequences)
                .stream(false)
                .temperature(temperature)
                .p(topP)
                .k(topK)
                .tools(toCohereTools(tools))
                .build();

        CohereChatResponse response = withRetry(() -> client.chat(request), maxRetries);

        return Response.from(
                toAiMessage(response),
                toTokenUsage(response.getMeta().getBilledUnits())
        );
    }

}
