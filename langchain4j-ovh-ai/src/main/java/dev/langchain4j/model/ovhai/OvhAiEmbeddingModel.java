package dev.langchain4j.model.ovhai;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import java.time.Duration;
import java.util.List;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.ovhai.internal.api.EmbeddingRequest;
import dev.langchain4j.model.ovhai.internal.api.EmbeddingResponse;
import dev.langchain4j.model.ovhai.internal.client.DefaultOvhAiClient;
import lombok.Builder;
import static java.util.stream.Collectors.toList;


/**
 * Represents an Anthropic language model with a Messages (chat) API.
 * <br>
 * More details are available <a href="https://docs.anthropic.com/claude/reference/messages_post">here</a>.
 * <br>
 * <br>
 * It supports tools. See more information <a href="https://docs.anthropic.com/claude/docs/tool-use">here</a>.
 * <br>
 * <br>
 * It supports {@link Image}s as inputs. {@link UserMessage}s can contain one or multiple {@link ImageContent}s.
 * {@link Image}s must not be represented as URLs; they should be Base64-encoded strings and include a {@code mimeType}.
 * <br>
 * <br>
 * The content of {@link SystemMessage}s is sent using the "system" parameter.
 * If there are multiple {@link SystemMessage}s, they are concatenated with a double newline (\n\n).
 */
public class OvhAiEmbeddingModel implements EmbeddingModel {

    private final DefaultOvhAiClient client;
    private final int maxRetries;

    /**
     * Constructs an instance of an {@code AnthropicChatModel} with the specified parameters.
     *
     * @param baseUrl       The base URL of the Anthropic API. Default: "https://api.anthropic.com/v1/"
     * @param apiKey        The API key for authentication with the Anthropic API.
     * @param version       The version of the Anthropic API. Default: "2023-06-01"
     * @param beta          The value of the "anthropic-beta" HTTP header. It is used when tools are present in the request. Default: "tools-2024-04-04"
     * @param modelName     The name of the Anthropic model to use. Default: "claude-3-haiku-20240307"
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
    private OvhAiEmbeddingModel(String baseUrl,
                               String apiKey,
                               Duration timeout,
                               Integer maxRetries,
                               Boolean logRequests,
                               Boolean logResponses) {
        this.client = DefaultOvhAiClient.builder()
                .baseUrl(getOrDefault(baseUrl, "https://multilingual-e5-base.endpoints.kepler.ai.cloud.ovh.net"))
                .apiKey(apiKey)
                .timeout(getOrDefault(timeout, Duration.ofSeconds(60)))
                .logRequests(getOrDefault(logRequests, false))
                .logResponses(getOrDefault(logResponses, false))
                .build();
        this.maxRetries = getOrDefault(maxRetries, 3);
    }

    /**
     * Creates an instance of {@code AnthropicChatModel} with the specified API key.
     *
     * @param apiKey the API key for authentication
     * @return an {@code AnthropicChatModel} instance
     */
    public static OvhAiEmbeddingModel withApiKey(String apiKey) {
        return builder().apiKey(apiKey).build();
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {

            EmbeddingRequest request = EmbeddingRequest.builder()
                            .input(textSegments.stream().map(TextSegment::text)
                                            .collect(toList()))
                            .build();

            EmbeddingResponse response = withRetry(() -> client.embed(request), maxRetries);

            List<Embedding> embeddings = response.getEmbeddings().stream().map(Embedding::from)
                            .collect(toList());

            // TokenUsage tokenUsage = new TokenUsage(response.getUsage().getTotalTokens(), 0);

            // return Response.from(embeddings, tokenUsage);
            return Response.from(embeddings);
    }
}
