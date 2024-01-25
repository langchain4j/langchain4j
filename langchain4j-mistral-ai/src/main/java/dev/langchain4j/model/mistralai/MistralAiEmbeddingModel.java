package dev.langchain4j.model.mistralai;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import lombok.Builder;

import java.time.Duration;
import java.util.List;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.mistralai.DefaultMistralAiHelper.*;
import static java.util.stream.Collectors.toList;

/**
 * Represents a Mistral AI embedding model, such as mistral-embed.
 * You can find description of parameters <a href="https://docs.mistral.ai/api/#operation/createEmbedding">here</a>.
 */
public class MistralAiEmbeddingModel implements EmbeddingModel {

    private final MistralAiClient client;
    private final String modelName;
    private final Integer maxRetries;

    /**
     * Constructs a new MistralAiEmbeddingModel instance.
     *
     * @param baseUrl      the base URL of the Mistral AI API. It use a default value if not specified
     * @param apiKey       the API key for authentication
     * @param modelName    the name of the embedding model. It uses a default value if not specified
     * @param timeout      the timeout duration for API requests. It uses a default value of 60 seconds if not specified
     *                     <p>
     *                     The default value is 60 seconds
     * @param logRequests  a flag indicating whether to log API requests
     * @param logResponses a flag indicating whether to log API responses
     * @param maxRetries   the maximum number of retries for API requests. It uses a default value of 3 if not specified
     */
    @Builder
    public MistralAiEmbeddingModel(String baseUrl,
                                   String apiKey,
                                   String modelName,
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
        this.modelName = getOrDefault(modelName, MistralAiEmbeddingModelName.MISTRAL_EMBED.toString());
        this.maxRetries = getOrDefault(maxRetries, 3);
    }

    /**
     * Creates a new MistralAiEmbeddingModel instance with the specified API key.
     *
     * @param apiKey the Mistral AI API key for authentication
     * @return a new MistralAiEmbeddingModel instance
     */
    public static MistralAiEmbeddingModel withApiKey(String apiKey) {
        return builder().apiKey(apiKey).build();
    }

    /**
     * Embeds a list of text segments using the Mistral AI embedding model.
     *
     * @param textSegments the list of text segments to embed
     * @return a Response object containing the embeddings and token usage information
     */
    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {

        MistralAiEmbeddingRequest request = MistralAiEmbeddingRequest.builder()
                .model(modelName)
                .input(textSegments.stream().map(TextSegment::text).collect(toList()))
                .encodingFormat(MISTRALAI_API_CREATE_EMBEDDINGS_ENCODING_FORMAT)
                .build();

        MistralAiEmbeddingResponse response = withRetry(() -> client.embedding(request), maxRetries);

        List<Embedding> embeddings = response.getData().stream()
                .map(mistralAiEmbedding -> Embedding.from(mistralAiEmbedding.getEmbedding()))
                .collect(toList());

        return Response.from(
                embeddings,
                tokenUsageFrom(response.getUsage())
        );
    }
}
